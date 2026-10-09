#!/usr/bin/env python3
"""Keep plan.md and STEPS.md in step with features.csv (the source of truth).

    python3 scripts/sync_features.py                    # sync plan.md + STEPS.md from the CSV
    python3 scripts/sync_features.py done ID [ID ...]   # mark features done (today), then sync
    python3 scripts/sync_features.py undo ID [ID ...]   # mark features todo again, then sync
    python3 scripts/sync_features.py --check            # exit 1 if anything is out of sync (CI)

features.csv columns: id, phase, step, section, feature, status (todo|done), done_on (YYYY-MM-DD).
plan.md:  every checkbox carries its id, e.g.  "- [ ] Play <!-- PLAY-01 -->".
STEPS.md: every step that builds features has a generated block
          "<!-- features:18 -->" ... "<!-- /features -->" listing them.
"""
import csv
import datetime
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSV_PATH = ROOT / "features.csv"
PLAN_PATH = ROOT / "plan.md"
STEPS_PATH = ROOT / "STEPS.md"
FIELDS = ["id", "phase", "step", "section", "feature", "status", "done_on"]
PHASES = {
    "0": "Foundation",
    "1": "Data foundation & basic replay",
    "2": "Telemetry, timeline & playback",
    "3": "Championship & analytics",
    "4": "Distributed systems & production",
    "5": "Data science & polish",
    "stretch": "Stretch goals",
}

CHECKBOX = re.compile(r"^(\s*- \[)[ x](\] .*<!-- ([A-Z]+-\d+) -->.*)$", re.M)
PROGRESS = re.compile(r"(<!-- progress:start -->\n).*?(<!-- progress:end -->)", re.S)
STEP_BLOCK = re.compile(r"(<!-- features:(\d+) -->\n).*?(<!-- /features -->)", re.S)


def fail(msg):
    sys.exit(f"sync_features: {msg}")


def load():
    with CSV_PATH.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    dupes = [i for i, n in Counter(r["id"] for r in rows).items() if n > 1]
    if dupes:
        fail(f"duplicate ids in features.csv: {dupes}")
    for r in rows:
        if r["status"] not in ("todo", "done"):
            fail(f"{r['id']}: status must be 'todo' or 'done', got {r['status']!r}")
        if not r["step"].isdigit():
            fail(f"{r['id']}: step must be a number, got {r['step']!r}")
    return rows


def save(rows):
    with CSV_PATH.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)


def set_status(rows, ids, status):
    by_id = {r["id"]: r for r in rows}
    unknown = [i for i in ids if i not in by_id]
    if unknown:
        fail(f"unknown ids: {unknown}")
    today = datetime.date.today().isoformat()
    for i in ids:
        by_id[i]["status"] = status
        by_id[i]["done_on"] = today if status == "done" else ""
    save(rows)


def box(r):
    return "x" if r["status"] == "done" else " "


def render_plan(text, rows):
    by_id = {r["id"]: r for r in rows}
    seen = set()

    def tick(m):
        fid = m.group(3)
        if fid not in by_id:
            fail(f"plan.md has {fid}, which is not in features.csv")
        seen.add(fid)
        return f"{m.group(1)}{box(by_id[fid])}{m.group(2)}"

    text = CHECKBOX.sub(tick, text)
    missing = set(by_id) - seen
    if missing:
        fail(f"features.csv ids with no checkbox in plan.md: {sorted(missing)}")

    done = sum(r["status"] == "done" for r in rows)
    lines = [f"**{done} / {len(rows)} features done ({100 * done // len(rows)}%)**", "",
             "| Phase | Done | Total | Progress |", "|---|---:|---:|---|"]
    by_phase = defaultdict(list)
    for r in rows:
        by_phase[r["phase"]].append(r)
    for p, name in PHASES.items():
        group = by_phase.get(p, [])
        if group:
            d = sum(r["status"] == "done" for r in group)
            bar = "█" * round(10 * d / len(group)) + "░" * (10 - round(10 * d / len(group)))
            lines.append(f"| {p} — {name} | {d} | {len(group)} | `{bar}` |")
    if not PROGRESS.search(text):
        fail("plan.md is missing the <!-- progress:start --> / <!-- progress:end --> markers")
    return PROGRESS.sub(lambda m: m.group(1) + "\n".join(lines) + "\n" + m.group(2), text)


def render_steps(text, rows):
    by_step = defaultdict(list)
    for r in rows:
        by_step[int(r["step"])].append(r)
    seen = set()

    def block(m):
        step = int(m.group(2))
        seen.add(step)
        group = by_step.get(step, [])
        if not group:
            body = "_No plan.md checklist items — this step is groundwork._\n"
        else:
            d = sum(r["status"] == "done" for r in group)
            body = f"**Checks off ({d}/{len(group)}):**\n\n" + "".join(
                f"- [{box(r)}] `{r['id']}` {r['feature']}\n" for r in group)
        return m.group(1) + body + m.group(3)

    text = STEP_BLOCK.sub(block, text)
    missing = set(by_step) - seen
    if missing:
        fail(f"features.csv uses steps with no <!-- features:N --> block in STEPS.md: {sorted(missing)}")
    return text


def main(argv):
    check = "--check" in argv
    args = [a for a in argv if a != "--check"]
    rows = load()
    if args and args[0] in ("done", "undo"):
        if check:
            fail("--check cannot be combined with done/undo")
        set_status(rows, args[1:], "done" if args[0] == "done" else "todo")
    elif args:
        fail(__doc__)

    stale = []
    for path, render in ((PLAN_PATH, render_plan), (STEPS_PATH, render_steps)):
        old = path.read_text(encoding="utf-8")
        new = render(old, rows)
        if new != old:
            stale.append(path.name)
            if not check:
                path.write_text(new, encoding="utf-8")

    done = sum(r["status"] == "done" for r in rows)
    if check:
        if stale:
            fail(f"out of sync with features.csv: {', '.join(stale)} — run python3 scripts/sync_features.py")
        print(f"in sync — {done}/{len(rows)} features done")
    else:
        print(f"{done}/{len(rows)} features done" + (f"; updated {', '.join(stale)}" if stale else "; nothing to update"))


if __name__ == "__main__":
    main(sys.argv[1:])

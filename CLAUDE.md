# Telemetrix

F1 race replay & analytics platform. Spring Boot 4.1 / Java 25 / Maven (`backend/`, always `./mvnw`),
PostgreSQL 16 via `docker-compose.yml`. Design: `plan.md`. Build order: `STEPS.md`. Reference
code for each Spring layer: `templates/`.

## Test-driven development — always

This project is built strictly test-first. The full rules and the test type per layer are in
`STEPS.md` → *How we work*; each step's **Tests first** list says what to write before the code.

- Red → green → refactor. Write the failing test first, **run it and show it failing for the right
  reason**, then write the minimum code to pass, then refactor with the suite green.
- Outside-in: start a feature with a failing outer test (API / WebSocket / Playwright), drive the
  internals with unit tests until it passes.
- No production code without a failing test that needs it; bug fixes start with a reproducing test.
- Never weaken, skip, `@Disabled` or delete a test to get green — fix the code, or fix the test in its
  own red → green cycle and say so.
- Inject `java.time.Clock`; never call the live OpenF1/Jolpica APIs from tests (use fixtures in
  `src/test/resources/fixtures/`).
- Run the relevant suite (`./mvnw verify` in `backend/`, `npm test` in `frontend/`) before calling
  anything done, and report the real result.

## Feature checklist — keep it current

`features.csv` is the source of truth for progress. Every deliverable in `plan.md` is a checkbox
tagged `<!-- ID -->`, and each `STEPS.md` step has a generated `<!-- features:N -->` block.

- When a feature from `plan.md` is implemented and its tests pass, mark it done in the same change:
  `python3 scripts/sync_features.py done <ID> [<ID> ...]` (repo root). Use `undo` to revert.
- Never tick checkboxes in `plan.md` or edit the `<!-- features:N -->` blocks in `STEPS.md` by hand;
  edit `features.csv` and run `python3 scripts/sync_features.py`.
- Adding a feature to `plan.md`: add the bullet as `- [ ] Text <!-- PREFIX-NN -->` (next free number
  for that prefix), add the matching `features.csv` row with its phase and step, then sync.
- Before finishing any task, `python3 scripts/sync_features.py --check` must pass.
- Keep `README.md` describing what actually exists, not what is planned.

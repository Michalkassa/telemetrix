# 4. Replay cars as lap progress along the track (video-style playback)

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

The replay shows each car as a coloured dot on a track map, with video-like controls: play,
pause, fast forward (up to 8×), rewind, and scrubbing to any moment.

The original plan imported full telemetry and location (~3.7 Hz per car) for every session and
streamed interpolated frames from the server. That is about a million rows and 40+ API requests per
session, which is slow to import, heavy to store, and needs server-side streaming state per viewer.

A dot on a 2D map doesn't need that detail. It only needs to know **how far around the lap** each
car is at any moment.

## Decision

Model every car's position as **lap progress**: a number from 0 to 1 (0–100%) along the track
outline, plus the lap number.

- **Timing points:** each lap gives 4 points per driver: lap start (0%), end of sector 1, end of
  sector 2, and lap end (100%). They come from OpenF1 `laps` (`date_start` + 3 sector durations),
  which is **1 request per session for all drivers**. Before 2023, Jolpica lap times give 2 points
  per lap (start and end), back to 1996.
- **Sector boundaries:** where sectors 1 and 2 end along the track (as a fraction of the outline)
  come from the track data when available. Otherwise they're estimated from the median ratio of
  sector time to lap time for that circuit.
- **Between points:** progress is interpolated linearly in time. A car's position is a **pure
  function**:

  ```
  position(driver, t) → (lap, progress)          // binary search + linear interpolation
  dot(driver, t)      → outline.pointAt(progress) // arc-length lookup on the track polyline
  ```

- **Running order** is sorted by `lap + progress`, so the leaderboard always matches the dots.
- **The browser gets the whole race's timing points in one request** (~20 drivers × ~60 laps × 4
  points ≈ 5,000 numbers, a few hundred KB) and runs the clock itself:
  - play: `t += elapsed × speed` every animation frame
  - pause: stop advancing `t`
  - fast forward: set `speed` (0.25× – 8×)
  - rewind / scrub / jump to lap: set `t`

  Every change is instant, like scrubbing a video, because nothing has to be recomputed or
  re-fetched.
- **Pit stops:** the car is shown in the pit lane (or marked "in pit") between OpenF1 `pit`
  entry and exit times. **Retirements:** the dot stops at its last progress, then disappears.
- **Full telemetry** (speed, throttle, brake traces) is a *separate, on-demand* view (Step 27). It
  is not part of replay.

## Consequences

- **Import:** a race's replay data takes seconds instead of minutes. No million-row tables are
  needed for replay.
- **Playback:** fully client-side. Pause, 8×, rewind and seek are instant, and there's no per-viewer
  server state. WebSockets are only needed later for synchronised group viewing or live mode.
- **Accuracy:** within a sector a car moves at a constant average speed, so braking zones aren't
  visible, and overtakes inside a sector appear at the next timing point. That's acceptable for a 2D
  dot map; the full-telemetry view covers detail.
- **History:** lap-level replay (2 timing points per lap) becomes possible back to 1996.
- **Lateral position:** cars always sit on the centre line of the track outline. Side-by-side
  battles overlap; dots get a small offset by position to stay visible.

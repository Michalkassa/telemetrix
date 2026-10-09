// One car's progress through a race (docs/adr/0004) as "laps done": 9.25 is a quarter of the way
// round lap 10. The same rules as the server's ProgressTimeline (contracts/progress-timeline.json):
// linear in time between timing points, on the grid (0) before the first, stays put after the last.

export interface Timeline {
  t: number[] // milliseconds, ascending
  laps: number[]
}

/** A timeline from raw points: in time order, one point per moment, and never backwards. */
export function timeline(t: number[], laps: number[]): Timeline {
  const order = t.map((_, i) => i).sort((a, b) => t[a] - t[b])
  const clean: Timeline = { t: [], laps: [] }
  for (const i of order) {
    const last = clean.t.length - 1
    if (last >= 0 && laps[i] < clean.laps[last]) continue // behind a point already reached: bad data
    if (last >= 0 && t[i] === clean.t[last]) {
      clean.laps[last] = laps[i] // same moment: keep the further one
    } else {
      clean.t.push(t[i])
      clean.laps.push(laps[i])
    }
  }
  return clean
}

export function progressAt(car: Timeline, t: number): number {
  const { t: times, laps } = car
  const n = times.length
  if (n === 0 || t < times[0]) return 0
  if (t >= times[n - 1]) return laps[n - 1]
  // binary search: the last point at or before t
  let lo = 0
  let hi = n - 1
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1
    if (times[mid] <= t) lo = mid
    else hi = mid
  }
  const share = (t - times[lo]) / (times[hi] - times[lo])
  return laps[lo] + share * (laps[hi] - laps[lo])
}

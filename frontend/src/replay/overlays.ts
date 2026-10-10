import type { Flag, Snapshot, WeatherReading } from '../api/types'
import { progressAt, reachedAt } from './progress'
import type { Race } from './race'

// What the replay's overlays show, as plain data worked out from the race and its state at a moment
// (docs/api.md: the replay, its snapshots and weather). Drawing it is ReplayPlayer's job.

// ---- sector times and colours (purple / green / yellow, as on F1's timing screens)

export interface SectorTime {
  car: number
  lap: number
  sector: 1 | 2 | 3
  endsAt: number // race time, ms
  ms: number
}

export type SectorColour = 'purple' | 'green' | 'yellow'

/** Every sector time the timing shows: a complete lap has four timing points, lap start to lap end. */
export function sectorTimes(race: Race): SectorTime[] {
  const times: SectorTime[] = []
  for (const car of race.cars) {
    const { t, laps } = car.timeline
    for (let i = 0; i + 3 < t.length; i++) {
      const start = laps[i]
      const lap = start + 1
      const whole = Number.isInteger(start) && laps[i + 3] === lap
      const inside = laps[i + 1] > start && laps[i + 2] < lap && laps[i + 1] < laps[i + 2]
      if (!whole || !inside) continue
      for (let s = 1; s <= 3; s++) {
        times.push({ car: car.number, lap, sector: s as 1 | 2 | 3, endsAt: t[i + s], ms: t[i + s] - t[i + s - 1] })
      }
    }
  }
  return times.sort((a, b) => a.endsAt - b.endsAt)
}

/**
 * Each car's latest time in each sector by `t`, coloured when it was set: purple if no one had been
 * faster in that sector, green if the car had never been faster, yellow otherwise.
 */
export function sectorColours(times: SectorTime[], t: number): Map<number, (SectorColour | null)[]> {
  const overall = [Infinity, Infinity, Infinity]
  const personal = new Map<number, number[]>()
  const colours = new Map<number, (SectorColour | null)[]>()
  for (const time of times) {
    if (time.endsAt > t) break
    const i = time.sector - 1
    const own = personal.get(time.car) ?? [Infinity, Infinity, Infinity]
    const colour: SectorColour = time.ms < overall[i] ? 'purple' : time.ms < own[i] ? 'green' : 'yellow'
    overall[i] = Math.min(overall[i], time.ms)
    own[i] = Math.min(own[i], time.ms)
    personal.set(time.car, own)
    const shown = colours.get(time.car) ?? [null, null, null]
    shown[i] = colour
    colours.set(time.car, shown)
  }
  return colours
}

export const SECTOR_COLOURS: Record<SectorColour, string> = {
  purple: '#a855f7',
  green: '#22c55e',
  yellow: '#eab308',
}

// ---- flags

const AMBER = '#facc15'

/** The banner over the track while the race is neutralised (yellows show on the sectors instead). */
export function banner(flag: Flag): { text: string; colour: string } | null {
  switch (flag) {
    case 'SAFETY_CAR':
      return { text: 'SAFETY CAR', colour: AMBER }
    case 'VSC':
      return { text: 'VIRTUAL SAFETY CAR', colour: AMBER }
    case 'VSC_ENDING':
      return { text: 'VSC ENDING', colour: AMBER }
    case 'RED':
      return { text: 'RED FLAG', colour: '#ef4444' }
    default:
      return null
  }
}

/** The track outline's colour: amber under the safety car or VSC, red under a red flag. */
export function trackColour(flag: Flag): string {
  switch (flag) {
    case 'SAFETY_CAR':
    case 'VSC':
    case 'VSC_ENDING':
      return '#a16207'
    case 'RED':
      return '#991b1b'
    default:
      return '#52525b'
  }
}

/**
 * The stretches of the lap under yellow, as progress ranges (0–1 round the lap; `to` past 1 runs
 * across the line). A marshalling sector runs from where it starts to where the next one does.
 */
export function yellowSegments(
  sectors: { number: number; progress: number }[],
  yellow: Record<string, 'YELLOW' | 'DOUBLE_YELLOW'>,
): { from: number; to: number; double: boolean }[] {
  const ordered = [...sectors].sort((a, b) => a.number - b.number)
  return ordered.flatMap((sector, i) => {
    const flag = yellow[String(sector.number)]
    if (!flag) return []
    const next = ordered[(i + 1) % ordered.length].progress
    const to = next > sector.progress ? next : next + 1
    return [{ from: sector.progress, to: Math.round(to * 1e9) / 1e9, double: flag === 'DOUBLE_YELLOW' }]
  })
}

/** Where to draw the safety car: a little ahead of the leader (laps done). */
export function safetyCarProgress(leaderLapsDone: number): number {
  return leaderLapsDone + 0.02
}

// ---- the derived state, weather and gaps at a moment

function latestAtOrBefore<T extends { t: number }>(items: T[], t: number): T | null {
  let lo = 0
  let hi = items.length - 1
  let found: T | null = null
  while (lo <= hi) {
    const mid = (lo + hi) >> 1
    if (items[mid].t <= t) {
      found = items[mid]
      lo = mid + 1
    } else {
      hi = mid - 1
    }
  }
  return found
}

/** Flag, yellow sectors, DRS, tyres, pit stops and penalties at `t` (snapshots in time order). */
export function derivedAt(snapshots: Snapshot[], t: number): Snapshot | null {
  return latestAtOrBefore(snapshots, t)
}

export function weatherAt(readings: WeatherReading[], t: number): WeatherReading | null {
  return latestAtOrBefore(readings, t)
}

export interface Gap {
  gap: number | null // seconds to the leader
  interval: number | null // seconds to the car ahead
  lapsDown: number
}

/** As the server works them out: how long ago the car ahead (or the leader) was where this car is now. */
export function gapsAt(race: Race, t: number): Map<number, Gap> {
  const order = race.cars
    .map((car) => ({ car, laps: progressAt(car.timeline, t) }))
    .sort((a, b) => b.laps - a.laps || a.car.number - b.car.number)
  const gaps = new Map<number, Gap>()
  const leader = order[0]
  order.forEach(({ car, laps }, i) => {
    const behind = (other: (typeof order)[number]) => {
      const was = reachedAt(other.car.timeline, laps)
      return was === null ? null : Math.max(0, Math.round(t - was)) / 1000
    }
    gaps.set(car.number, {
      gap: i === 0 ? 0 : behind(leader),
      interval: i === 0 ? null : behind(order[i - 1]),
      lapsDown: Math.max(0, Math.floor(leader.laps - laps)),
    })
  })
  return gaps
}

// ---- the pit lane

/** The point a share (0–1) of the way along an open path, by distance. */
export function pathPointAt(path: [number, number][], share: number): [number, number] {
  if (path.length === 0) return [0, 0]
  const lengths = [0]
  for (let i = 1; i < path.length; i++) {
    lengths.push(lengths[i - 1] + Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]))
  }
  const wanted = Math.min(Math.max(share, 0), 1) * lengths[lengths.length - 1]
  for (let i = 1; i < path.length; i++) {
    if (lengths[i] >= wanted) {
      const segment = lengths[i] - lengths[i - 1]
      const f = segment === 0 ? 0 : (wanted - lengths[i - 1]) / segment
      return [path[i - 1][0] + f * (path[i][0] - path[i - 1][0]), path[i - 1][1] + f * (path[i][1] - path[i - 1][1])]
    }
  }
  return path[path.length - 1]
}

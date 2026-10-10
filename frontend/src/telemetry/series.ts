import type { LapAnalysis, TracePoint } from '../api/types'

// The telemetry view's chart rows: one per point of the reference lap, by distance. A second lap is
// laid over it by share of the lap (how far round each car is), as the backend compares them.

export interface Row {
  distance: number
  speed: number | null
  throttle: number | null
  brake: number | null
  gear: number | null
  rpm: number | null
  otherSpeed?: number | null
  otherThrottle?: number | null
  otherBrake?: number | null
  otherGear?: number | null
  otherRpm?: number | null
}

export function mergeLaps(reference: LapAnalysis, other?: LapAnalysis): Row[] {
  return reference.trace.map((p) => {
    const row: Row = {
      distance: p.distance,
      speed: p.speed,
      throttle: p.throttle,
      brake: p.brake,
      gear: p.gear,
      rpm: p.rpm,
    }
    if (other && reference.distance > 0) {
      const o = pointAtDistance(other.trace, (p.distance / reference.distance) * other.distance)
      Object.assign(row, {
        otherSpeed: o?.speed ?? null,
        otherThrottle: o?.throttle ?? null,
        otherBrake: o?.brake ?? null,
        otherGear: o?.gear ?? null,
        otherRpm: o?.rpm ?? null,
      })
    }
    return row
  })
}

// the last point at or before `metres`
function pointAtDistance(trace: TracePoint[], metres: number): TracePoint | null {
  let found: TracePoint | null = null
  for (const p of trace) {
    if (p.distance > metres + 1e-9) break
    found = p
  }
  return found
}

/** 92.608 → "1:32.608" */
export function formatLapTime(seconds: number): string {
  const ms = Math.round(seconds * 1000)
  return `${Math.floor(ms / 60_000)}:${String(Math.floor(ms / 1000) % 60).padStart(2, '0')}.${String(ms % 1000).padStart(3, '0')}`
}

/** 0.322 → "+0.322"; negative with a real minus sign */
export function formatDelta(seconds: number | null): string {
  if (seconds === null) return '—'
  return `${seconds < 0 ? '−' : '+'}${Math.abs(seconds).toFixed(3)}`
}

/**
 * Where the lap's car is at a replay race time (ms after `clockStart`): metres round the lap, or null
 * when that moment isn't in this lap.
 */
export function cursorDistance(lap: LapAnalysis, clockStart: string, raceTime: number): number | null {
  const into = raceTime - (Date.parse(lap.startsAt) - Date.parse(clockStart))
  if (into < 0 || into > lap.lapTime * 1000) return null
  const trace = lap.trace
  for (let i = 1; i < trace.length; i++) {
    if (trace[i].time >= into) {
      const a = trace[i - 1]
      const b = trace[i]
      const share = b.time === a.time ? 0 : (into - a.time) / (b.time - a.time)
      return a.distance + share * (b.distance - a.distance)
    }
  }
  return trace.length ? trace[trace.length - 1].distance : null
}

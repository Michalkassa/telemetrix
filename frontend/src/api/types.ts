// The backend's JSON shapes (docs/api.md).

export interface Season {
  year: number
  raceCount: number
}

export interface Circuit {
  ref: string
  name: string
  locality: string | null
  country: string | null
}

export interface RaceSummary {
  id: number
  round: number
  name: string
  date: string
  circuit: Circuit
  hasOpenF1Data: boolean
}

export interface SeasonRaces {
  year: number
  races: RaceSummary[]
}

export interface Session {
  id: number
  name: string
  startsAt: string
  endsAt: string | null
}

export interface RaceDetail {
  id: number
  season: number
  round: number
  name: string
  date: string
  circuit: Circuit
  openf1MeetingKey: number | null
  importedAt: string | null
  sessions: Session[]
}

export type ImportState = 'NOT_IMPORTED' | 'IMPORTING' | 'READY' | 'FAILED' | 'NOT_AVAILABLE'

export interface ImportStatus {
  raceId: number
  state: ImportState
  since: string | null
  message: string | null
}

export interface Layout {
  circuit: string
  year: number
  rotation: number
  length: number
  boundingBox: { minX: number; minY: number; maxX: number; maxY: number }
  points: [number, number][] // already rotated for display
  startFinish: [number, number]
  sectorEnds: number[] | null
  marshalSectors?: { number: number; progress: number }[] // where each marshalling sector starts
  pitLane?: [number, number][] // already rotated; empty when unknown
}

export interface ReplayDriver {
  number: number
  code: string
  name: string
  team: string
  teamColour: string | null // hex without '#'
}

export interface CarTiming {
  number: number
  t: number[] // ms after clockStart
  laps: number[] // laps done: 9.25 = a quarter of the way round lap 10
  pits: { lap: number | null; from: number; to: number }[]
  finished: boolean
  retiredAt: number | null
}

export interface Replay {
  sessionId: number
  raceId: number
  session: string
  clockStart: string
  chequeredAt: string
  totalLaps: number
  layout: Layout | null
  drivers: ReplayDriver[]
  timing: CarTiming[]
  weather?: WeatherReading[]
}

export interface WeatherReading {
  t: number // ms after clockStart
  airTemperature: number | null
  trackTemperature: number | null
  humidity: number | null
  rainfall: boolean
  windSpeed: number | null
}

export type Flag = 'GREEN' | 'YELLOW' | 'SAFETY_CAR' | 'VSC' | 'VSC_ENDING' | 'RED'

export interface SnapshotCar {
  compound: string | null
  stintLapStart: number | null
  ageAtStart: number
  pitStops: number
  inPit: boolean
  penaltySeconds: number
  pendingPenalties: number
  compounds: string[]
}

/** GET /api/sessions/{id}/replay/snapshots: the derived race state at each change (docs/api.md). */
export interface Snapshot {
  t: number
  lap: number
  reason: string
  flag: Flag
  yellowSectors: Record<string, 'YELLOW' | 'DOUBLE_YELLOW'>
  drsEnabled: boolean
  cars: Record<string, SnapshotCar>
}

/** GET /api/sessions/{id}/telemetry/lap: one lap against distance (docs/api.md). */
export interface TracePoint {
  time: number // ms since the lap started
  distance: number // m from the line
  speed: number | null
  throttle: number | null
  brake: number | null
  gear: number | null
  rpm: number | null
  drs: number | null
}

export interface LapAnalysis {
  sessionId: number
  driver: number
  lap: number
  startsAt: string
  lapTime: number // s
  sectors: (number | null)[]
  tyre: { compound: string; stint: number; age: number } | null
  estimates: { fuelKg: number; ersDeployedMj: number; ersHarvestedMj: number; note: string }
  distance: number
  trace: TracePoint[]
}

/** GET /api/sessions/{id}/telemetry/compare: other − reference, seconds. */
export interface LapComparison {
  reference: LapAnalysis
  other: LapAnalysis
  lapDelta: number
  sectorDeltas: (number | null)[]
  delta: { distance: number; delta: number }[]
}

/** GET /api/races/{id}/timeline (docs/api.md). */
export interface TimelineEvent {
  type: string
  at: string
  t: number // ms after clockStart
  lap: number | null
  endsAt: string | null
  endLap: number | null
  severity: 'LOW' | 'MEDIUM' | 'HIGH'
  participants: { number: number; code: string | null }[]
  summary: string
  link: string | null
}

export interface Timeline {
  raceId: number
  sessionId: number
  clockStart: string
  events: TimelineEvent[]
}

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
}

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

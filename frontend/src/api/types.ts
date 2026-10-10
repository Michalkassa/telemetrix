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

/** GET /api/races/{id}/results: the classification, in finishing order. */
export interface RaceResultRow {
  position: number | null
  code: string | null // missing for many drivers before the 2000s
  givenName: string
  surname: string
  team: string
  number: number | null
  grid: number | null
  laps: number | null
  timeMs: number | null
  status: string | null
  statusDetail: string | null
  classified: boolean | null
  points: number | null
}

export interface RaceResults {
  raceId: number
  results: RaceResultRow[]
}

/** GET /api/races/{id}/lap-times: race lap times per car, in finishing order. */
export interface LapTimes {
  raceId: number
  source: 'OPENF1' | 'JOLPICA' | 'NONE'
  drivers: {
    number: number
    code: string | null
    surname: string | null
    position: number | null
    laps: { lap: number; ms: number }[]
  }[]
}

/** GET /api/seasons/{year}/standings */
export interface ChampionshipStandings {
  season: number
  round: number
  rounds: number
  drivers: {
    position: number
    ref: string
    code: string | null
    givenName: string
    surname: string
    team: string | null
    points: number
    wins: number
  }[]
  constructors: { position: number; ref: string; name: string; points: number; wins: number }[]
}

/** GET /api/seasons/{year}/results-grid */
export interface ResultsGrid {
  season: number
  races: { round: number; raceId: number; name: string; date: string }[]
  drivers: {
    position: number
    ref: string
    code: string | null
    surname: string
    points: number
    cells: { round: number; raceId: number; position: number | null; points: number; status: string | null }[]
  }[]
}

/** GET /api/seasons/{year}/progression */
export interface Progression {
  season: number
  drivers: { ref: string; code: string | null; surname: string; rounds: { round: number; points: number }[] }[]
}

/** GET /api/drivers: everyone who has raced, the latest season first. */
export interface DriverSummary {
  ref: string
  code: string | null
  givenName: string
  surname: string
  nationality: string | null
  firstSeason: number
  lastSeason: number
  starts: number
  wins: number
}

/** GET /api/drivers/{id}/stats */
export interface DriverStats {
  driver: { ref: string; code: string | null; givenName: string; surname: string }
  career: {
    starts: number
    wins: number
    podiums: number
    dnfs: number
    poles: number
    fastestLaps: number
    averageFinish: number | null
    positionsGained: number
    averagePositionsGained: number | null
    points: number
  }
  wetDry: { wet: Conditions; dry: Conditions }
  tracks: {
    circuit: string
    name: string
    starts: number
    wins: number
    podiums: number
    bestFinish: number | null
    averageFinish: number | null
  }[]
  pitStops: { stops: number; stopsPerRace: number | null; averageLaneSeconds: number | null }
  tyres: { compound: string; stints: number; laps: number; share: number; averageStintLaps: number }[]
  seasons: { season: number; starts: number; wins: number; points: number; careerWins: number }[]
}

export interface Conditions {
  races: number
  wins: number
  podiums: number
  averageFinish: number | null
}

/** GET /api/drivers/{id}/races: every Grand Prix entered, the newest first. */
export interface RaceLogEntry {
  season: number
  round: number
  raceId: number
  race: string
  teamRef: string
  team: string
  grid: number | null
  position: number | null
  classified: boolean
  status: string | null
  statusDetail: string | null
  points: number
}

/** GET /api/constructors: every constructor that has raced, with the seasons it raced in. */
export interface ConstructorSummary {
  ref: string
  name: string
  nationality: string | null
  seasons: number[]
  starts: number
  wins: number
}

/** GET /api/constructors/{id}/stats?season= */
export interface ConstructorStats {
  constructor: { ref: string; name: string }
  season: number
  reliability: {
    starts: number
    finishes: number
    dnfs: number
    mechanical: number
    incident: number
    unknown: number
    causes: { cause: string; count: number }[]
  }
  pitStops: {
    stops: number
    medianLaneSeconds: number | null
    rank: number | null
    teams: number
    fieldMedianLaneSeconds: number | null
  }
  pace: { raceGapPercent: number | null; qualifyingGapPercent: number | null }
  trend: {
    rounds: { round: number; race: string; qualifyingGapPercent: number | null; raceGapPercent: number | null }[]
    qualifyingSlope: number | null
    raceSlope: number | null
  }
  strategies: { stops: number; finishes: number; averagePositionsGained: number }[]
}

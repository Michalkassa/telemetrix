import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getJson, postJson } from './client'
import type {
  ImportStatus,
  LapAnalysis,
  LapComparison,
  RaceDetail,
  Replay,
  Season,
  SeasonRaces,
  Snapshot,
  Timeline,
  RaceResults,
  LapTimes,
  ChampionshipStandings,
  ResultsGrid,
  Progression,
  DriverSummary,
  DriverStats,
  RaceLogEntry,
  ConstructorSummary,
  ConstructorStats,
} from './types'

// Data hooks: React Query caches each answer, so going back and forth between pages is instant.

export function useSeasons() {
  return useQuery({ queryKey: ['seasons'], queryFn: () => getJson<Season[]>('/api/seasons') })
}

export function useSeasonRaces(year: number) {
  return useQuery({
    queryKey: ['seasons', year, 'races'],
    queryFn: () => getJson<SeasonRaces>(`/api/seasons/${year}/races`),
  })
}

export function useRace(raceId: number) {
  return useQuery({ queryKey: ['races', raceId], queryFn: () => getJson<RaceDetail>(`/api/races/${raceId}`) })
}

// Opening a race downloads its data if it isn't stored yet (docs/api.md): POST once, then poll the
// status every second while it's IMPORTING.
export function useRaceImport(raceId: number) {
  const client = useQueryClient()
  const queryKey = ['races', raceId, 'import']
  return useQuery({
    queryKey,
    queryFn: () =>
      client.getQueryData(queryKey)
        ? getJson<ImportStatus>(`/api/races/${raceId}/import-status`)
        : postJson<ImportStatus>(`/api/races/${raceId}/import`),
    refetchInterval: (query) => (query.state.data?.state === 'IMPORTING' ? 1000 : false),
  })
}

export function useReplay(sessionId: number | undefined) {
  return useQuery({
    queryKey: ['sessions', sessionId, 'replay'],
    queryFn: () => getJson<Replay>(`/api/sessions/${sessionId}/replay`),
    enabled: sessionId !== undefined,
    staleTime: Infinity, // a race that has happened doesn't change
  })
}

// The race state at every change (flags, yellow sectors, DRS, tyres, penalties) for the overlays. The
// replay plays without them, so a failure isn't retried or shown.
export function useSnapshots(sessionId: number | undefined) {
  return useQuery({
    queryKey: ['sessions', sessionId, 'snapshots'],
    queryFn: () => getJson<Snapshot[]>(`/api/sessions/${sessionId}/replay/snapshots`),
    enabled: sessionId !== undefined,
    staleTime: Infinity,
    retry: false,
  })
}

// One lap's telemetry against distance (no lap: the driver's fastest). The first request for a driver
// downloads their telemetry on the server (a few seconds); it never changes after that.
export function useLapAnalysis(sessionId: number | undefined, driver: number | undefined, lap?: number) {
  const query = new URLSearchParams({ driver: String(driver) })
  if (lap) query.set('lap', String(lap))
  return useQuery({
    queryKey: ['sessions', sessionId, 'telemetry', 'lap', driver, lap ?? 'fastest'],
    queryFn: () => getJson<LapAnalysis>(`/api/sessions/${sessionId}/telemetry/lap?${query}`),
    enabled: sessionId !== undefined && driver !== undefined,
    staleTime: Infinity,
  })
}

export function useLapComparison(
  sessionId: number | undefined,
  driver: number | undefined,
  lap: number | undefined,
  vs: number | undefined,
  vsLap: number | undefined,
) {
  const query = new URLSearchParams({ driver: String(driver), vs: String(vs) })
  if (lap) query.set('lap', String(lap))
  if (vsLap) query.set('vsLap', String(vsLap))
  return useQuery({
    queryKey: ['sessions', sessionId, 'telemetry', 'compare', driver, lap ?? 'fastest', vs, vsLap ?? 'fastest'],
    queryFn: () => getJson<LapComparison>(`/api/sessions/${sessionId}/telemetry/compare?${query}`),
    enabled: sessionId !== undefined && driver !== undefined && vs !== undefined,
    staleTime: Infinity,
  })
}

// The race's events (GET /api/races/{id}/timeline), for the player's event list and scrub-bar markers.
export function useTimeline(raceId: number | undefined) {
  return useQuery({
    queryKey: ['races', raceId, 'timeline'],
    queryFn: () => getJson<Timeline>(`/api/races/${raceId}/timeline`),
    enabled: raceId !== undefined,
    retry: false,
  })
}

export function useRaceResults(raceId: number) {
  return useQuery({
    queryKey: ['races', raceId, 'results'],
    queryFn: () => getJson<RaceResults>(`/api/races/${raceId}/results`),
  })
}

export function useLapTimes(raceId: number) {
  return useQuery({
    queryKey: ['races', raceId, 'lap-times'],
    queryFn: () => getJson<LapTimes>(`/api/races/${raceId}/lap-times`),
  })
}

export function useStandings(year: number) {
  return useQuery({
    queryKey: ['seasons', year, 'standings'],
    queryFn: () => getJson<ChampionshipStandings>(`/api/seasons/${year}/standings`),
  })
}

export function useResultsGrid(year: number) {
  return useQuery({
    queryKey: ['seasons', year, 'results-grid'],
    queryFn: () => getJson<ResultsGrid>(`/api/seasons/${year}/results-grid`),
  })
}

export function useProgression(year: number) {
  return useQuery({
    queryKey: ['seasons', year, 'progression'],
    queryFn: () => getJson<Progression>(`/api/seasons/${year}/progression`),
  })
}

export function useDrivers() {
  return useQuery({ queryKey: ['drivers'], queryFn: () => getJson<DriverSummary[]>('/api/drivers') })
}

export function useDriverStats(ref: string) {
  return useQuery({
    queryKey: ['drivers', ref, 'stats'],
    queryFn: () => getJson<DriverStats>(`/api/drivers/${ref}/stats`),
  })
}

export function useDriverRaces(ref: string) {
  return useQuery({
    queryKey: ['drivers', ref, 'races'],
    queryFn: () => getJson<RaceLogEntry[]>(`/api/drivers/${ref}/races`),
  })
}

export function useConstructors() {
  return useQuery({ queryKey: ['constructors'], queryFn: () => getJson<ConstructorSummary[]>('/api/constructors') })
}

/** A team's season; without one, its latest. */
export function useConstructorStats(ref: string, season: number | null) {
  return useQuery({
    queryKey: ['constructors', ref, 'stats', season],
    queryFn: () =>
      getJson<ConstructorStats>(`/api/constructors/${ref}/stats${season === null ? '' : `?season=${season}`}`),
  })
}

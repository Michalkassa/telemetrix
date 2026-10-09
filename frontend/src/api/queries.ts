import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getJson, postJson } from './client'
import type { ImportStatus, RaceDetail, Replay, Season, SeasonRaces } from './types'

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

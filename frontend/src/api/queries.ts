import { useQuery } from '@tanstack/react-query'
import { getJson } from './client'
import type { RaceDetail, Season, SeasonRaces } from './types'

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

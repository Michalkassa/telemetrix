import { Navigate } from 'react-router'
import { useSeasons } from '../api/queries'

// /races: straight to the newest season that has races.
export function RacesIndex() {
  const seasons = useSeasons()
  if (seasons.isError) return <p role="alert">Could not load seasons: {seasons.error.message}</p>
  const newest = seasons.data?.find((s) => s.raceCount > 0)
  if (newest) return <Navigate to={`/races/${newest.year}`} replace />
  if (seasons.data) return <p>No races imported yet.</p>
  return <p>Loading seasons…</p>
}

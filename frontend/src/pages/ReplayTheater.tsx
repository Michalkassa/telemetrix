import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { useSeasonRaces, useSeasons } from '../api/queries'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { formatDate } from '../components/format'

// Replays start in 2023 (OpenF1, docs/adr/0003).
const FIRST_REPLAY_SEASON = 2023

// /replay — straight to the newest season that has races.
export function ReplayTheaterIndex() {
  const seasons = useSeasons()
  if (seasons.isError) return <p role="alert">Could not load seasons: {seasons.error.message}</p>
  const newest = seasons.data?.find((s) => s.raceCount > 0 && s.year >= FIRST_REPLAY_SEASON)
  if (newest) return <Navigate to={`/replay/${newest.year}`} replace />
  if (seasons.data) return <p>No races with a replay imported yet.</p>
  return <p>Loading seasons…</p>
}

// /replay/:year — the Replay Theater lens: pick a season, then a Grand Prix, and watch it (the race
// hub's replay page).
export function ReplayTheater() {
  const year = Number(useParams().year)
  const navigate = useNavigate()
  const seasons = useSeasons()
  const races = useSeasonRaces(year)
  const replays = races.data?.races.filter((race) => race.hasOpenF1Data) ?? []

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: 'Dashboard', to: '/dashboard' },
          { label: 'Replay Theater', to: '/replay' },
          { label: String(year) },
        ]}
      />
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-bold">Replay Theater</h1>
        <label className="flex items-center gap-2 text-sm text-zinc-400">
          Season
          <select
            value={year}
            onChange={(e) => navigate(`/replay/${e.target.value}`)}
            className="rounded-md border border-zinc-700 bg-zinc-900 px-3 py-1.5 text-zinc-100"
          >
            {(seasons.data ?? [{ year, raceCount: 0 }])
              .filter((s) => (s.raceCount > 0 && s.year >= FIRST_REPLAY_SEASON) || s.year === year)
              .map((s) => (
                <option key={s.year} value={s.year}>
                  {s.year}
                </option>
              ))}
          </select>
        </label>
      </div>

      {races.isPending && <p className="text-zinc-400">Loading races…</p>}
      {races.isError && (
        <p role="alert" className="rounded-md border border-red-900 bg-red-950 p-4 text-red-200">
          {races.error.message}
        </p>
      )}
      {races.data && replays.length === 0 && <p className="text-zinc-400">No replays for {year}.</p>}
      {replays.length > 0 && (
        <ol aria-label="Replays" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {replays.map((race) => (
            <li key={race.id}>
              <Link
                to={`/races/${year}/${race.id}/replay`}
                className="block rounded-xl border border-zinc-800 bg-zinc-900 p-4 hover:border-red-600"
              >
                <span className="block text-xs uppercase tracking-wider text-zinc-500">Round {race.round}</span>
                <span className="mt-1 block font-semibold">▶ {race.name}</span>
                <span className="mt-1 block text-sm text-zinc-400">
                  {race.circuit.locality ?? race.circuit.name} · {formatDate(race.date)}
                </span>
              </Link>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

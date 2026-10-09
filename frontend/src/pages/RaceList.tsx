import { Link, useNavigate, useParams } from 'react-router'
import { useSeasonRaces, useSeasons } from '../api/queries'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { formatDate } from '../components/format'

// /races/:year — a season's Grand Prix, with a year picker. The year is in the URL.
export function RaceList() {
  const year = Number(useParams().year)
  const navigate = useNavigate()
  const seasons = useSeasons()
  const races = useSeasonRaces(year)

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: 'Dashboard', to: '/dashboard' }, { label: 'Grand Prix', to: '/races' }, { label: String(year) }]} />
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-bold">{year} Grand Prix</h1>
        <label className="flex items-center gap-2 text-sm text-zinc-400">
          Season
          <select
            value={year}
            onChange={(e) => navigate(`/races/${e.target.value}`)}
            className="rounded-md border border-zinc-700 bg-zinc-900 px-3 py-1.5 text-zinc-100"
          >
            {(seasons.data ?? [{ year, raceCount: 0 }])
              .filter((s) => s.raceCount > 0 || s.year === year)
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
      {races.data && (
        <ol aria-label="Races" className="divide-y divide-zinc-800 rounded-xl border border-zinc-800">
          {races.data.races.map((race) => (
            <li key={race.id}>
              <Link to={`/races/${year}/${race.id}`} className="flex items-center gap-4 px-4 py-3 hover:bg-zinc-900">
                <span className="w-8 text-right font-mono text-zinc-500">{race.round}</span>
                <span className="flex-1">
                  <span className="block font-medium">{race.name}</span>
                  <span className="block text-sm text-zinc-400">
                    {race.circuit.name}
                    {race.circuit.locality && ` · ${race.circuit.locality}`}
                  </span>
                </span>
                <span className="text-sm text-zinc-400">{formatDate(race.date)}</span>
              </Link>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

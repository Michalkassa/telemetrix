import { Link } from 'react-router'
import { useSeasonRaces } from '../../api/queries'
import { formatDate } from '../../components/format'
import { Loading, Problem, SeasonLeaf } from './SeasonLeaf'

// /seasons/:year/races — the calendar; each race leads to its hub.
export function SeasonRacesPage() {
  return <SeasonLeaf title="Races">{(year) => <Races year={year} />}</SeasonLeaf>
}

function Races({ year }: { year: number }) {
  const races = useSeasonRaces(year)
  if (races.isPending) return <Loading what="races" />
  if (races.isError) return <Problem error={races.error} />
  return (
    <ol aria-label="Races" className="divide-y divide-zinc-800 rounded-xl border border-zinc-800">
      {races.data.races.map((race) => (
        <li key={race.id}>
          <Link to={`/races/${year}/${race.id}`} className="flex items-center gap-4 px-4 py-2 hover:bg-zinc-900">
            <span className="w-8 text-right font-mono text-zinc-500">{race.round}</span>
            <span className="flex-1">{race.name}</span>
            <span className="text-sm text-zinc-400">{formatDate(race.date)}</span>
          </Link>
        </li>
      ))}
    </ol>
  )
}

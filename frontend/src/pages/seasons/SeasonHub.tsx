import { Link } from 'react-router'
import { useStandings } from '../../api/queries'
import { Loading, Problem, SeasonLeaf } from './SeasonLeaf'

// /seasons/:year — a championship year: who won, and its pages.
export function SeasonHub() {
  return <SeasonLeaf>{(year) => <Hub year={year} />}</SeasonLeaf>
}

function Hub({ year }: { year: number }) {
  const standings = useStandings(year)
  const base = `/seasons/${year}`
  const pages = [
    ['standings/drivers', "Drivers' standings", 'Points, wins and position'],
    ['standings/constructors', "Constructors' standings", 'The teams'],
    ['results', 'Results grid', 'Every race, driver by driver'],
    ['races', 'Races', 'The calendar, each race with its hub'],
    ['compare', 'Compare', 'Points progression: two drivers, two seasons'],
  ]
  return (
    <div className="space-y-6">
      {standings.isPending && <Loading what="standings" />}
      {standings.isError && <Problem error={standings.error} />}
      {standings.data && standings.data.drivers.length > 0 && (
        <p className="text-lg">
          Champion:{' '}
          <strong>
            {standings.data.drivers[0].givenName} {standings.data.drivers[0].surname}
          </strong>{' '}
          ({standings.data.drivers[0].points} pts)
          {standings.data.constructors.length > 0 && (
            <>
              {' '}
              · Constructors: <strong>{standings.data.constructors[0].name}</strong>
            </>
          )}
          <span className="ml-2 text-sm text-zinc-500">
            after {standings.data.round} of {standings.data.rounds} rounds
          </span>
        </p>
      )}
      <ul aria-label="Season pages" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        {pages.map(([path, name, about]) => (
          <li key={path}>
            <Link
              to={`${base}/${path}`}
              className="block h-full rounded-xl border border-zinc-800 bg-zinc-900 p-4 hover:border-red-600"
            >
              <span className="block font-semibold">{name}</span>
              <span className="mt-1 block text-sm text-zinc-400">{about}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

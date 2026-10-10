import { Link } from 'react-router'
import { useSeasons } from '../../api/queries'
import { Breadcrumbs } from '../../components/Breadcrumbs'
import { Loading, Problem } from './SeasonLeaf'

// /seasons — every championship year, newest first.
export function SeasonsIndex() {
  const seasons = useSeasons()
  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: 'Dashboard', to: '/dashboard' }, { label: 'Seasons' }]} />
      <h1 className="text-3xl font-bold">Seasons</h1>
      {seasons.isPending && <Loading what="seasons" />}
      {seasons.isError && <Problem error={seasons.error} />}
      {seasons.data && (
        <ol aria-label="Seasons" className="grid grid-cols-3 gap-2 sm:grid-cols-5 lg:grid-cols-8">
          {seasons.data.map((s) => (
            <li key={s.year}>
              <Link
                to={`/seasons/${s.year}`}
                className="block rounded-lg border border-zinc-800 bg-zinc-900 px-3 py-2 text-center hover:border-red-600"
              >
                <span className="block font-semibold">{s.year}</span>
                <span className="block text-xs text-zinc-500">{s.raceCount} races</span>
              </Link>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

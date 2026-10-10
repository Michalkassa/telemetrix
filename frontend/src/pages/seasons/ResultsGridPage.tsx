import { Link } from 'react-router'
import { useResultsGrid } from '../../api/queries'
import type { ResultsGrid } from '../../api/types'
import { Loading, Problem, SeasonLeaf } from './SeasonLeaf'

// /seasons/:year/results — every Grand Prix of the season, driver by driver; each result links to its
// race's hub.
export function ResultsGridPage() {
  return <SeasonLeaf title="Results">{(year) => <Grid year={year} />}</SeasonLeaf>
}

const STATUS: Record<string, string> = {
  DISQUALIFIED: 'DSQ',
  DID_NOT_START: 'DNS',
  DID_NOT_QUALIFY: 'DNQ',
}

function cellText(cell: ResultsGrid['drivers'][number]['cells'][number]): string {
  if (cell.position !== null) return String(cell.position)
  return STATUS[cell.status ?? ''] ?? 'Ret'
}

function cellColour(cell: ResultsGrid['drivers'][number]['cells'][number]): string {
  if (cell.position === 1) return 'bg-amber-400 text-zinc-900'
  if (cell.position === 2 || cell.position === 3) return 'bg-zinc-300 text-zinc-900'
  if (cell.points > 0) return 'bg-emerald-800'
  if (cell.position === null) return 'text-red-400'
  return 'text-zinc-300'
}

function Grid({ year }: { year: number }) {
  const grid = useResultsGrid(year)
  if (grid.isPending) return <Loading what="results" />
  if (grid.isError) return <Problem error={grid.error} />
  const { races, drivers } = grid.data
  return (
    <div className="overflow-x-auto">
      <table aria-label="Results grid" className="text-xs tabular-nums">
        <thead>
          <tr>
            <th className="sticky left-0 bg-zinc-950 px-2 text-left font-normal text-zinc-400">Driver</th>
            {races.map((r) => (
              <th key={r.round} className="px-1 font-normal">
                <Link
                  to={`/races/${year}/${r.raceId}`}
                  aria-label={r.name}
                  title={r.name}
                  className="text-zinc-400 hover:text-zinc-100"
                >
                  {r.round}
                </Link>
              </th>
            ))}
            <th className="px-2 text-right font-normal text-zinc-400">Pts</th>
          </tr>
        </thead>
        <tbody>
          {drivers.map((d) => {
            const byRound = new Map(d.cells.map((c) => [c.round, c]))
            return (
              <tr key={d.ref}>
                <th scope="row" className="sticky left-0 whitespace-nowrap bg-zinc-950 px-2 text-left font-normal">
                  <span className="mr-1 text-zinc-500">{d.position}</span>
                  {d.code && <span className="font-semibold">{d.code} </span>}
                  {d.surname}
                </th>
                {races.map((r) => {
                  const cell = byRound.get(r.round)
                  return (
                    <td key={r.round} className="p-0.5 text-center">
                      {cell && (
                        <Link
                          to={`/races/${year}/${cell.raceId}`}
                          title={`${r.name}: ${cellText(cell)}${cell.points ? ` (${cell.points} pts)` : ''}`}
                          className={`block min-w-7 rounded px-1 py-0.5 hover:outline hover:outline-red-600 ${cellColour(cell)}`}
                        >
                          {cellText(cell)}
                        </Link>
                      )}
                    </td>
                  )
                })}
                <td className="px-2 text-right font-semibold">{d.points}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

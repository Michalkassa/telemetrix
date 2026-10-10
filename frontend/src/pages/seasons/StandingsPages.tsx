import { useState } from 'react'
import { useStandings } from '../../api/queries'
import { Loading, Problem, SeasonLeaf } from './SeasonLeaf'

// /seasons/:year/standings/drivers and …/constructors — sortable tables (click a column heading).

interface Column<T> {
  key: string
  label: string
  value: (row: T) => string | number
  numeric?: boolean
  testId?: string
}

function SortableTable<T>({ label, rows, columns }: { label: string; rows: T[]; columns: Column<T>[] }) {
  const [sort, setSort] = useState<{ key: string; descending: boolean } | null>(null)
  const column = columns.find((c) => c.key === sort?.key)
  const sorted = column
    ? [...rows].sort((a, b) => {
        const x = column.value(a)
        const y = column.value(b)
        const order = typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y))
        return sort!.descending ? -order : order
      })
    : rows
  const click = (c: Column<T>) =>
    setSort((s) =>
      s?.key === c.key ? { key: c.key, descending: !s.descending } : { key: c.key, descending: !!c.numeric },
    )
  return (
    <table aria-label={label} className="w-full text-sm tabular-nums">
      <thead className="text-left text-zinc-400">
        <tr>
          {columns.map((c) => (
            <th key={c.key} className={`py-1 font-normal ${c.numeric ? 'text-right' : ''}`}>
              <button type="button" onClick={() => click(c)} className="hover:text-zinc-100">
                {c.label}
                {sort?.key === c.key && (sort.descending ? ' ↓' : ' ↑')}
              </button>
            </th>
          ))}
        </tr>
      </thead>
      <tbody className="divide-y divide-zinc-800">
        {sorted.map((row, i) => (
          <tr key={i}>
            {columns.map((c) => (
              <td key={c.key} data-testid={c.testId} className={`py-1.5 ${c.numeric ? 'text-right' : ''}`}>
                {c.value(row)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export function DriverStandingsPage() {
  return <SeasonLeaf title="Drivers' standings">{(year) => <Drivers year={year} />}</SeasonLeaf>
}

function Drivers({ year }: { year: number }) {
  const standings = useStandings(year)
  if (standings.isPending) return <Loading what="standings" />
  if (standings.isError) return <Problem error={standings.error} />
  type Row = (typeof standings.data.drivers)[number]
  return (
    <SortableTable<Row>
      label="Drivers' standings"
      rows={standings.data.drivers}
      columns={[
        { key: 'position', label: 'Pos', value: (d) => d.position, numeric: true },
        { key: 'driver', label: 'Driver', value: (d) => `${d.surname}, ${d.givenName}` },
        { key: 'team', label: 'Team', value: (d) => d.team ?? '' },
        { key: 'wins', label: 'Wins', value: (d) => d.wins, numeric: true, testId: 'wins' },
        { key: 'points', label: 'Points', value: (d) => d.points, numeric: true },
      ]}
    />
  )
}

export function ConstructorStandingsPage() {
  return <SeasonLeaf title="Constructors' standings">{(year) => <Constructors year={year} />}</SeasonLeaf>
}

function Constructors({ year }: { year: number }) {
  const standings = useStandings(year)
  if (standings.isPending) return <Loading what="standings" />
  if (standings.isError) return <Problem error={standings.error} />
  type Row = (typeof standings.data.constructors)[number]
  return (
    <>
      <SortableTable<Row>
        label="Constructors' standings"
        rows={standings.data.constructors}
        columns={[
          { key: 'position', label: 'Pos', value: (c) => c.position, numeric: true },
          { key: 'name', label: 'Constructor', value: (c) => c.name },
          { key: 'wins', label: 'Wins', value: (c) => c.wins, numeric: true },
          { key: 'points', label: 'Points', value: (c) => c.points, numeric: true },
        ]}
      />
      {year < 1979 && <p className="text-sm text-zinc-500">Before 1979 these are simple sums, not that era's rules.</p>}
    </>
  )
}

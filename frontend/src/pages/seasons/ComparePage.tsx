import { CartesianGrid, Line, LineChart, Tooltip, XAxis, YAxis } from 'recharts'
import { useSearchParams } from 'react-router'
import { useProgression, useSeasons } from '../../api/queries'
import type { Progression } from '../../api/types'
import { Loading, SeasonLeaf } from './SeasonLeaf'

// /seasons/:year/compare?a=2021:max_verstappen&b=2020:hamilton — two drivers' championship points round
// by round, from the same season or two. By default this season's champion against the runner-up.
export function ComparePage() {
  return <SeasonLeaf title="Compare">{(year) => <Compare year={year} />}</SeasonLeaf>
}

function parse(value: string | null, fallbackYear: number): { year: number; ref: string | null } {
  const [y, ref] = (value ?? '').split(':')
  return { year: Number(y) || fallbackYear, ref: ref || null }
}

function Compare({ year }: { year: number }) {
  const [search, setSearch] = useSearchParams()
  const a = parse(search.get('a'), year)
  const b = parse(search.get('b'), year)
  const seasonA = useProgression(a.year)
  const seasonB = useProgression(b.year)
  const seasons = useSeasons()
  if (seasonA.isPending || seasonB.isPending) return <Loading what="points" />
  if (!seasonA.data || !seasonB.data) return <p role="alert">Couldn't load those seasons.</p>

  const driverA = seasonA.data.drivers.find((d) => d.ref === a.ref) ?? seasonA.data.drivers[0]
  const driverB =
    seasonB.data.drivers.find((d) => d.ref === b.ref) ??
    seasonB.data.drivers[a.year === b.year ? 1 : 0] ??
    seasonB.data.drivers[0]
  const series = [
    { key: 'a', year: a.year, driver: driverA, colour: '#ef4444' },
    { key: 'b', year: b.year, driver: driverB, colour: '#3b82f6' },
  ].filter((s) => s.driver)
  const rounds = Math.max(0, ...series.map((s) => s.driver!.rounds.length))
  const rows = Array.from({ length: rounds }, (_, i) => {
    const row: Record<string, number> = { round: i + 1 }
    for (const s of series) {
      const r = s.driver!.rounds[i]
      if (r) row[s.key] = r.points
    }
    return row
  })
  const label = (s: (typeof series)[number]) =>
    `${s.driver!.code ?? s.driver!.surname} ${s.year} · ${s.driver!.rounds.at(-1)?.points ?? 0}`

  const choose = (key: 'a' | 'b', y: number, ref: string) => {
    const next = new URLSearchParams(search)
    next.set(key, `${y}:${ref}`)
    setSearch(next)
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-6 text-sm">
        <Picker
          label="First"
          years={seasons.data?.map((s) => s.year) ?? [a.year]}
          year={a.year}
          progression={seasonA.data}
          driver={driverA?.ref}
          onChange={(y, r) => choose('a', y, r)}
        />
        <Picker
          label="Second"
          years={seasons.data?.map((s) => s.year) ?? [b.year]}
          year={b.year}
          progression={seasonB.data}
          driver={driverB?.ref}
          onChange={(y, r) => choose('b', y, r)}
        />
      </div>
      <h2 className="text-lg font-semibold">Points progression</h2>
      <div className="overflow-x-auto">
        <LineChart width={900} height={380} data={rows} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
          <CartesianGrid stroke="#27272a" />
          <XAxis
            dataKey="round"
            stroke="#71717a"
            fontSize={11}
            label={{ value: 'round', position: 'insideBottomRight', offset: -4, fill: '#71717a' }}
          />
          <YAxis stroke="#71717a" fontSize={11} width={48} />
          <Tooltip contentStyle={{ background: '#18181b', border: '1px solid #3f3f46' }} />
          {series.map((s) => (
            <Line key={s.key} dataKey={s.key} name={label(s)} stroke={s.colour} dot={false} isAnimationActive={false} />
          ))}
        </LineChart>
      </div>
      <ul aria-label="Series" className="flex gap-6 text-sm text-zinc-300">
        {series.map((s) => (
          <li key={s.key} className="flex items-center gap-2">
            <span className="h-0.5 w-6" style={{ backgroundColor: s.colour }} />
            {label(s)}
          </li>
        ))}
      </ul>
    </div>
  )
}

function Picker(props: {
  label: string
  years: number[]
  year: number
  progression: Progression
  driver: string | undefined
  onChange: (year: number, ref: string) => void
}) {
  return (
    <fieldset className="flex items-center gap-2 text-zinc-400">
      <legend className="sr-only">{props.label}</legend>
      {props.label}
      <select
        aria-label={`${props.label} season`}
        value={props.year}
        onChange={(e) => props.onChange(Number(e.target.value), '')}
        className="rounded-md border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
      >
        {props.years.map((y) => (
          <option key={y} value={y}>
            {y}
          </option>
        ))}
      </select>
      <select
        aria-label={`${props.label} driver`}
        value={props.driver ?? ''}
        onChange={(e) => props.onChange(props.year, e.target.value)}
        className="rounded-md border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
      >
        {props.progression.drivers.map((d) => (
          <option key={d.ref} value={d.ref}>
            {d.code ?? d.surname}
          </option>
        ))}
      </select>
    </fieldset>
  )
}

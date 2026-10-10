import type { ReactNode } from 'react'
import { Link, NavLink, useSearchParams } from 'react-router'
import { useConstructors, useDrivers } from '../../api/queries'
import { Breadcrumbs } from '../../components/Breadcrumbs'
import { seasonSpan } from '../../components/format'
import { Loading, Problem } from '../seasons/SeasonLeaf'

// /drivers and /teams — everyone who has raced, the latest season first, narrowed by a search (?q=).

function Directory({ title, search, children }: { title: string; search: string; children: (q: string) => ReactNode }) {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: 'Dashboard', to: '/dashboard' }, { label: title }]} />
      <h1 className="text-3xl font-bold">Drivers &amp; Teams</h1>
      <nav aria-label="Drivers & Teams" className="flex gap-2 text-sm">
        {[
          ['/drivers', 'Drivers'],
          ['/teams', 'Teams'],
        ].map(([to, label]) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `rounded-full border px-4 py-1.5 ${isActive ? 'border-red-600 text-zinc-100' : 'border-zinc-700 text-zinc-400 hover:text-zinc-100'}`
            }
          >
            {label}
          </NavLink>
        ))}
      </nav>
      <input
        type="search"
        aria-label={search}
        placeholder={search}
        value={q}
        onChange={(e) => setParams(e.target.value ? { q: e.target.value } : {}, { replace: true })}
        className="w-full max-w-sm rounded-md border border-zinc-700 bg-zinc-900 px-3 py-1.5 text-sm"
      />
      {children(q.trim().toLowerCase())}
    </div>
  )
}

export function DriversDirectory() {
  return (
    <Directory title="Drivers" search="Search drivers">
      {(q) => <Drivers q={q} />}
    </Directory>
  )
}

function Drivers({ q }: { q: string }) {
  const drivers = useDrivers()
  if (drivers.isPending) return <Loading what="drivers" />
  if (drivers.isError) return <Problem error={drivers.error} />
  const shown = drivers.data.filter((d) => `${d.givenName} ${d.surname}`.toLowerCase().includes(q))
  return (
    <table aria-label="Drivers" className="w-full text-sm tabular-nums">
      <thead className="text-left text-zinc-400">
        <tr>
          <th className="py-1 font-normal">Driver</th>
          <th className="py-1 font-normal">Nationality</th>
          <th className="py-1 font-normal">Seasons</th>
          <th className="py-1 text-right font-normal">Starts</th>
          <th className="py-1 text-right font-normal">Wins</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-zinc-800">
        {shown.map((d) => (
          <tr key={d.ref}>
            <td className="py-1.5">
              {d.code && <span className="mr-2 font-semibold text-zinc-400">{d.code}</span>}
              <Link to={`/drivers/${d.ref}`} className="hover:text-red-400 hover:underline">
                {d.givenName} {d.surname}
              </Link>
            </td>
            <td className="py-1.5 text-zinc-400">{d.nationality}</td>
            <td className="py-1.5">{seasonSpan(d.firstSeason, d.lastSeason)}</td>
            <td className="py-1.5 text-right">{d.starts}</td>
            <td className="py-1.5 text-right">{d.wins}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export function TeamsDirectory() {
  return (
    <Directory title="Teams" search="Search teams">
      {(q) => <Teams q={q} />}
    </Directory>
  )
}

function Teams({ q }: { q: string }) {
  const teams = useConstructors()
  if (teams.isPending) return <Loading what="teams" />
  if (teams.isError) return <Problem error={teams.error} />
  const shown = teams.data.filter((t) => t.name.toLowerCase().includes(q))
  return (
    <table aria-label="Teams" className="w-full text-sm tabular-nums">
      <thead className="text-left text-zinc-400">
        <tr>
          <th className="py-1 font-normal">Team</th>
          <th className="py-1 font-normal">Nationality</th>
          <th className="py-1 font-normal">Seasons</th>
          <th className="py-1 text-right font-normal">Starts</th>
          <th className="py-1 text-right font-normal">Wins</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-zinc-800">
        {shown.map((t) => (
          <tr key={t.ref}>
            <td className="py-1.5">
              <Link to={`/teams/${t.ref}`} className="hover:text-red-400 hover:underline">
                {t.name}
              </Link>
            </td>
            <td className="py-1.5 text-zinc-400">{t.nationality}</td>
            <td className="py-1.5">
              {seasonSpan(t.seasons[0], t.seasons[t.seasons.length - 1])}
              <span className="ml-1 text-zinc-500">({t.seasons.length})</span>
            </td>
            <td className="py-1.5 text-right">{t.starts}</td>
            <td className="py-1.5 text-right">{t.wins}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

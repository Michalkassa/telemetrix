import type { ReactNode } from 'react'
import { useParams, useSearchParams } from 'react-router'
import { useConstructors, useConstructorStats } from '../../api/queries'
import type { ConstructorStats } from '../../api/types'
import { Breadcrumbs } from '../../components/Breadcrumbs'
import { Loading, Problem } from '../seasons/SeasonLeaf'
import { Tabs } from './Figures'

// The frame of a team page (/teams/:ref…?season=): breadcrumbs, the season picker and the tabs. Without
// ?season= it is the team's latest season.
export function TeamLeaf({ tab, children }: { tab?: string; children: (stats: ConstructorStats) => ReactNode }) {
  const ref = useParams().ref!
  const [params, setParams] = useSearchParams()
  const season = Number(params.get('season')) || null
  const stats = useConstructorStats(ref, season)
  const teams = useConstructors()
  const seasons = [...(teams.data?.find((t) => t.ref === ref)?.seasons ?? [])].reverse()
  const name = stats.data?.constructor.name ?? '…'
  const query = stats.data ? `?season=${stats.data.season}` : ''
  const base = `/teams/${ref}`
  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: 'Dashboard', to: '/dashboard' },
          { label: 'Teams', to: '/teams' },
          tab ? { label: name, to: base + query } : { label: name },
          ...(tab ? [{ label: tab }] : []),
        ]}
      />
      <div className="flex flex-wrap items-center gap-4">
        {stats.data && <h1 className="text-3xl font-bold">{name}</h1>}
        {stats.data && seasons.length > 0 && (
          <label className="flex items-center gap-2 text-sm text-zinc-400">
            Season
            <select
              value={stats.data.season}
              onChange={(e) => setParams({ season: e.target.value })}
              className="rounded-md border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
            >
              {seasons.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      <Tabs
        label="Team pages"
        items={[
          [base + query, 'Overview'],
          [`${base}/reliability${query}`, 'Reliability'],
          [`${base}/pace${query}`, 'Pace'],
        ]}
      />
      {stats.isPending && <Loading what="the team" />}
      {stats.isError && <Problem error={stats.error} />}
      {stats.data && children(stats.data)}
    </div>
  )
}

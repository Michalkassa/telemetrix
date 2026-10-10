import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useDriverRaces, useDriverStats } from '../../api/queries'
import type { Conditions, DriverStats, RaceLogEntry } from '../../api/types'
import { Breadcrumbs } from '../../components/Breadcrumbs'
import { Loading, Problem } from '../seasons/SeasonLeaf'
import { decimal } from '../../components/format'
import { Figures, Tabs } from './Figures'

// /drivers/:ref — a driver's hub: Stats · Season by season · Race log. The URL uses the driver's ref
// (max_verstappen), not the three-letter code: most drivers before the 2000s have none, and some share
// one (VER is Vergne and Verstappen).

function DriverLeaf({ tab, children }: { tab?: string; children: (stats: DriverStats) => ReactNode }) {
  const ref = useParams().ref!
  const stats = useDriverStats(ref)
  const name = stats.data ? `${stats.data.driver.givenName} ${stats.data.driver.surname}` : '…'
  const base = `/drivers/${ref}`
  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: 'Dashboard', to: '/dashboard' },
          { label: 'Drivers', to: '/drivers' },
          tab ? { label: name, to: base } : { label: name },
          ...(tab ? [{ label: tab }] : []),
        ]}
      />
      {stats.data && (
        <h1 className="text-3xl font-bold">
          {stats.data.driver.code && (
            <span aria-hidden="true" className="mr-3 text-zinc-500">
              {stats.data.driver.code}
            </span>
          )}
          {name}
        </h1>
      )}
      <Tabs
        label="Driver pages"
        items={[
          [base, 'Stats'],
          [`${base}/seasons`, 'Season by season'],
          [`${base}/races`, 'Race log'],
        ]}
      />
      {stats.isPending && <Loading what="the driver" />}
      {stats.isError && <Problem error={stats.error} />}
      {stats.data && children(stats.data)}
    </div>
  )
}

export function DriverStatsPage() {
  return <DriverLeaf>{(stats) => <Stats stats={stats} />}</DriverLeaf>
}

function Stats({ stats }: { stats: DriverStats }) {
  const c = stats.career
  return (
    <div className="space-y-8">
      <Figures
        label="Career"
        items={[
          ['Starts', c.starts],
          ['Wins', c.wins],
          ['Podiums', c.podiums],
          ['Poles', c.poles],
          ['Fastest laps', c.fastestLaps],
          ['DNFs', c.dnfs],
          ['Average finish', decimal(c.averageFinish)],
          ['Places gained', `${c.positionsGained >= 0 ? '+' : ''}${c.positionsGained}`],
          ['Gained a race', decimal(c.averagePositionsGained)],
          ['Points', c.points],
        ]}
      />
      <p className="text-xs text-zinc-500">
        Poles are race grid position 1. Places gained run from grid to finish. Fastest laps are recorded from 2004.
      </p>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">Wet and dry</h2>
        {stats.wetDry.wet.races + stats.wetDry.dry.races === 0 ? (
          <p className="text-sm text-zinc-400">No weather yet: it comes with each race opened in the app (2023 on).</p>
        ) : (
          <table aria-label="Wet and dry" className="text-sm tabular-nums">
            <thead className="text-left text-zinc-400">
              <tr>
                <th className="pr-6 font-normal"></th>
                <th className="pr-6 text-right font-normal">Races</th>
                <th className="pr-6 text-right font-normal">Wins</th>
                <th className="pr-6 text-right font-normal">Podiums</th>
                <th className="text-right font-normal">Avg finish</th>
              </tr>
            </thead>
            <tbody>
              <ConditionsRow name="Wet" c={stats.wetDry.wet} />
              <ConditionsRow name="Dry" c={stats.wetDry.dry} />
            </tbody>
          </table>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">Pit stops and tyres</h2>
        <p className="text-sm">
          {stats.pitStops.stops} stops ({decimal(stats.pitStops.stopsPerRace)} a race), average{' '}
          {decimal(stats.pitStops.averageLaneSeconds)} s in the pit lane{' '}
          <span className="text-zinc-500">(from 2011)</span>
        </p>
        {stats.tyres.length > 0 && (
          <table aria-label="Tyres" className="text-sm tabular-nums">
            <thead className="text-left text-zinc-400">
              <tr>
                <th className="pr-6 font-normal">Compound</th>
                <th className="pr-6 text-right font-normal">Stints</th>
                <th className="pr-6 text-right font-normal">Laps</th>
                <th className="pr-6 text-right font-normal">Share</th>
                <th className="text-right font-normal">Laps a stint</th>
              </tr>
            </thead>
            <tbody>
              {stats.tyres.map((t) => (
                <tr key={t.compound}>
                  <td className="pr-6">{t.compound}</td>
                  <td className="pr-6 text-right">{t.stints}</td>
                  <td className="pr-6 text-right">{t.laps}</td>
                  <td className="pr-6 text-right">{(t.share * 100).toFixed(0)}%</td>
                  <td className="text-right">{decimal(t.averageStintLaps, 1)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">Tracks</h2>
        <table aria-label="Tracks" className="w-full text-sm tabular-nums">
          <thead className="text-left text-zinc-400">
            <tr>
              <th className="py-1 font-normal">Circuit</th>
              <th className="py-1 text-right font-normal">Starts</th>
              <th className="py-1 text-right font-normal">Wins</th>
              <th className="py-1 text-right font-normal">Podiums</th>
              <th className="py-1 text-right font-normal">Best</th>
              <th className="py-1 text-right font-normal">Avg finish</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-800">
            {stats.tracks.map((t) => (
              <tr key={t.circuit}>
                <td className="py-1.5">{t.name}</td>
                <td className="py-1.5 text-right">{t.starts}</td>
                <td className="py-1.5 text-right">{t.wins}</td>
                <td className="py-1.5 text-right">{t.podiums}</td>
                <td className="py-1.5 text-right">{t.bestFinish ?? '—'}</td>
                <td className="py-1.5 text-right">{decimal(t.averageFinish)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  )
}

function ConditionsRow({ name, c }: { name: string; c: Conditions }) {
  return (
    <tr>
      <th scope="row" className="pr-6 text-left font-normal">
        {name}
      </th>
      <td className="pr-6 text-right">{c.races}</td>
      <td className="pr-6 text-right">{c.wins}</td>
      <td className="pr-6 text-right">{c.podiums}</td>
      <td className="text-right">{decimal(c.averageFinish)}</td>
    </tr>
  )
}

export function DriverSeasonsPage() {
  return (
    <DriverLeaf tab="Season by season">
      {(stats) => (
        <table aria-label="Seasons" className="w-full text-sm tabular-nums">
          <thead className="text-left text-zinc-400">
            <tr>
              <th className="py-1 font-normal">Season</th>
              <th className="py-1 text-right font-normal">Starts</th>
              <th className="py-1 text-right font-normal">Wins</th>
              <th className="py-1 text-right font-normal">Points</th>
              <th className="py-1 text-right font-normal">Career wins</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-800">
            {stats.seasons.map((s) => (
              <tr key={s.season}>
                <td className="py-1.5">
                  <Link to={`/seasons/${s.season}`} className="hover:text-red-400 hover:underline">
                    {s.season}
                  </Link>
                </td>
                <td className="py-1.5 text-right">{s.starts}</td>
                <td className="py-1.5 text-right">{s.wins}</td>
                <td className="py-1.5 text-right">{s.points}</td>
                <td className="py-1.5 text-right">{s.careerWins}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </DriverLeaf>
  )
}

export function DriverRacesPage() {
  return <DriverLeaf tab="Race log">{(stats) => <RaceLog driver={stats.driver.ref} />}</DriverLeaf>
}

function RaceLog({ driver }: { driver: string }) {
  const races = useDriverRaces(driver)
  if (races.isPending) return <Loading what="races" />
  if (races.isError) return <Problem error={races.error} />
  return (
    <table aria-label="Race log" className="w-full text-sm tabular-nums">
      <thead className="text-left text-zinc-400">
        <tr>
          <th className="py-1 font-normal">Season</th>
          <th className="py-1 font-normal">Race</th>
          <th className="py-1 font-normal">Team</th>
          <th className="py-1 text-right font-normal">Grid</th>
          <th className="py-1 pl-4 font-normal">Result</th>
          <th className="py-1 text-right font-normal">Points</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-zinc-800">
        {races.data.map((r) => (
          <tr key={r.raceId}>
            <td className="py-1.5 text-zinc-400">
              {r.season} · R{r.round}
            </td>
            <td className="py-1.5">
              <Link to={`/races/${r.season}/${r.raceId}`} className="hover:text-red-400 hover:underline">
                {r.race}
              </Link>
            </td>
            <td className="py-1.5">
              <Link to={`/teams/${r.teamRef}?season=${r.season}`} className="hover:text-red-400 hover:underline">
                {r.team}
              </Link>
            </td>
            <td className="py-1.5 text-right">{r.grid || '—'}</td>
            <td className="py-1.5 pl-4">{result(r)}</td>
            <td className="py-1.5 text-right">{r.points}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

// "P1", "P12 · +1 Lap", "DNF · Engine", "DNS", "DSQ"
function result(r: RaceLogEntry) {
  const detail = r.status !== 'FINISHED' && r.statusDetail ? ` · ${r.statusDetail}` : ''
  if (r.status === 'DID_NOT_START') return 'DNS'
  if (r.status === 'DID_NOT_QUALIFY') return 'DNQ'
  if (r.status === 'DISQUALIFIED') return 'DSQ'
  if (r.status?.startsWith('RETIRED')) return `DNF${detail}`
  return `P${r.position}${detail}`
}

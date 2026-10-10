import { decimal, percent } from '../../components/format'
import { Figures } from './Figures'
import { TeamLeaf } from './TeamLeaf'

// /teams/:ref?season= (Overview) and /teams/:ref/reliability — a constructor's season.

export function TeamOverviewPage() {
  return (
    <TeamLeaf>
      {(stats) => (
        <div className="space-y-8">
          <Figures
            label="Season overview"
            items={[
              ['Finishes', `${stats.reliability.finishes} of ${stats.reliability.starts}`],
              ['Pit stops', stats.pitStops.rank === null ? '—' : `${stats.pitStops.rank} of ${stats.pitStops.teams}`],
              [
                'Median stop',
                stats.pitStops.medianLaneSeconds === null ? '—' : `${decimal(stats.pitStops.medianLaneSeconds)} s`,
              ],
              ['Race pace gap', percent(stats.pace.raceGapPercent)],
              ['Qualifying gap', percent(stats.pace.qualifyingGapPercent)],
            ]}
          />
          <p className="text-xs text-zinc-500">
            Pit stops rank the team&apos;s median time in the pit lane against the other teams (from 2011). Pace gaps
            are to the fastest team, race by race, then the season&apos;s median (lap times from 1996).
          </p>
          <section className="space-y-3">
            <h2 className="text-xl font-semibold">Strategies</h2>
            {stats.strategies.length === 0 ? (
              <p className="text-sm text-zinc-400">No pit-stop data for this season (it starts in 2011).</p>
            ) : (
              <table aria-label="Strategies" className="text-sm tabular-nums">
                <thead className="text-left text-zinc-400">
                  <tr>
                    <th className="pr-6 font-normal">Stops</th>
                    <th className="pr-6 text-right font-normal">Finishes</th>
                    <th className="text-right font-normal">Places gained a race</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.strategies.map((s) => (
                    <tr key={s.stops}>
                      <td className="pr-6">{s.stops}</td>
                      <td className="pr-6 text-right">{s.finishes}</td>
                      <td className="text-right">
                        {s.averagePositionsGained > 0 ? '+' : ''}
                        {decimal(s.averagePositionsGained)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </div>
      )}
    </TeamLeaf>
  )
}

export function TeamReliabilityPage() {
  return (
    <TeamLeaf tab="Reliability">
      {(stats) => {
        const r = stats.reliability
        return (
          <div className="space-y-6">
            <Figures
              label="Reliability"
              items={[
                ['Finishes', `${r.finishes} of ${r.starts}`],
                ['DNFs', r.dnfs],
                ['Mechanical', r.mechanical],
                ['Incidents', r.incident],
                ['Cause unknown', r.unknown],
              ]}
            />
            {r.unknown > 0 && (
              <p className="text-xs text-zinc-500">From 2023 the results only say &quot;Retired&quot;, not why.</p>
            )}
            {r.causes.length === 0 ? (
              <p className="text-sm text-zinc-400">No retirements this season.</p>
            ) : (
              <table aria-label="Retirements by cause" className="text-sm tabular-nums">
                <thead className="text-left text-zinc-400">
                  <tr>
                    <th className="pr-6 font-normal">Cause</th>
                    <th className="text-right font-normal">Retirements</th>
                  </tr>
                </thead>
                <tbody>
                  {r.causes.map((c) => (
                    <tr key={c.cause}>
                      <td className="pr-6">{c.cause}</td>
                      <td className="text-right">{c.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )
      }}
    </TeamLeaf>
  )
}

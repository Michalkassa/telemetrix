import { CartesianGrid, Legend, Line, LineChart, Tooltip, XAxis, YAxis } from 'recharts'
import type { ConstructorStats } from '../../api/types'
import { decimal, percent } from '../../components/format'
import { Figures } from './Figures'
import { TeamLeaf } from './TeamLeaf'

// /teams/:ref/pace?season= — the gaps to the fastest team, and how they moved through the season (the
// development trend). Loaded on its own, with its chart library.
export function TeamPacePage() {
  return <TeamLeaf tab="Pace">{(stats) => <Pace stats={stats} />}</TeamLeaf>
}

// "closing in by 0.03 points a round"
function slope(value: number | null) {
  if (value === null) return '—'
  return `${value <= 0 ? 'closing in' : 'falling back'} by ${Math.abs(value).toFixed(3)} points a round`
}

function Pace({ stats }: { stats: ConstructorStats }) {
  const rounds = stats.trend.rounds
  return (
    <div className="space-y-6">
      <Figures
        label="Pace"
        items={[
          ['Race pace gap', percent(stats.pace.raceGapPercent)],
          ['Qualifying gap', percent(stats.pace.qualifyingGapPercent)],
          [
            'Median stop',
            stats.pitStops.medianLaneSeconds === null ? '—' : `${decimal(stats.pitStops.medianLaneSeconds)} s`,
          ],
          [
            'Field median stop',
            stats.pitStops.fieldMedianLaneSeconds === null
              ? '—'
              : `${decimal(stats.pitStops.fieldMedianLaneSeconds)} s`,
          ],
        ]}
      />
      <p className="text-sm text-zinc-400">
        Qualifying: {slope(stats.trend.qualifyingSlope)} · Race pace: {slope(stats.trend.raceSlope)}
      </p>
      {rounds.length === 0 ? (
        <p className="text-sm text-zinc-400">No lap or qualifying times for this season (lap times start in 1996).</p>
      ) : (
        <>
          <div data-testid="trend-chart" className="overflow-x-auto">
            <LineChart width={760} height={280} data={rounds} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
              <CartesianGrid stroke="#27272a" />
              <XAxis dataKey="round" stroke="#a1a1aa" />
              <YAxis stroke="#a1a1aa" unit="%" />
              <Tooltip
                contentStyle={{ background: '#18181b', border: '1px solid #3f3f46' }}
                formatter={(v) => `${Number(v).toFixed(2)}%`}
                labelFormatter={(round) => rounds.find((r) => r.round === round)?.race ?? `Round ${round}`}
              />
              <Legend />
              <Line dataKey="qualifyingGapPercent" name="Qualifying gap" stroke="#3b82f6" dot={false} connectNulls />
              <Line dataKey="raceGapPercent" name="Race pace gap" stroke="#ef4444" dot={false} connectNulls />
            </LineChart>
          </div>
          <table aria-label="Development trend" className="w-full text-sm tabular-nums">
            <thead className="text-left text-zinc-400">
              <tr>
                <th className="py-1 font-normal">Round</th>
                <th className="py-1 font-normal">Race</th>
                <th className="py-1 text-right font-normal">Qualifying gap</th>
                <th className="py-1 text-right font-normal">Race pace gap</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800">
              {rounds.map((r) => (
                <tr key={r.round}>
                  <td className="py-1.5">{r.round}</td>
                  <td className="py-1.5">{r.race}</td>
                  <td className="py-1.5 text-right">{percent(r.qualifyingGapPercent)}</td>
                  <td className="py-1.5 text-right">{percent(r.raceGapPercent)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}

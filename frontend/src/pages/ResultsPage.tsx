import { useRaceResults } from '../api/queries'
import type { RaceResultRow } from '../api/types'
import { RaceLeaf } from './RaceLeaf'

// /races/:year/:raceId/results — the race's classification (the history import, 1950 on).
export function ResultsPage() {
  return <RaceLeaf title="Results">{(raceId) => <ResultsTable raceId={raceId} />}</RaceLeaf>
}

function ResultsTable({ raceId }: { raceId: number }) {
  const results = useRaceResults(raceId)
  if (results.isPending) return <p className="text-zinc-400">Loading results…</p>
  if (results.isError) return <p role="alert">{results.error.message}</p>
  const rows = results.data.results
  if (rows.length === 0) return <p className="text-zinc-400">No results imported for this race yet.</p>
  const winner = rows[0]
  return (
    <table aria-label="Results" className="w-full text-sm tabular-nums">
      <thead className="text-left text-zinc-400">
        <tr>
          <th className="py-1 font-normal">Pos</th>
          <th className="font-normal">No</th>
          <th className="font-normal">Driver</th>
          <th className="font-normal">Team</th>
          <th className="text-right font-normal">Grid</th>
          <th className="text-right font-normal">Laps</th>
          <th className="text-right font-normal">Time / status</th>
          <th className="text-right font-normal">Pts</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-zinc-800">
        {rows.map((r, i) => (
          <tr key={`${r.number}-${i}`}>
            <td className="py-1.5">{r.position ?? '—'}</td>
            <td className="text-zinc-400">{r.number ?? ''}</td>
            <td>
              <span className="font-semibold">{r.code ?? ''}</span> {r.givenName} {r.surname}
            </td>
            <td className="text-zinc-400">{r.team}</td>
            <td className="text-right">{r.grid || 'pit'}</td>
            <td className="text-right">{r.laps ?? ''}</td>
            <td className="text-right">{timeOrStatus(r, winner, i)}</td>
            <td className="text-right">{r.points ?? ''}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

// the winner's race time, the gap for cars on the lead lap, otherwise why ("+1 Lap", "Engine")
function timeOrStatus(row: RaceResultRow, winner: RaceResultRow, index: number): string {
  if (index === 0 && row.timeMs !== null) return raceTime(row.timeMs)
  if (row.timeMs !== null && winner.timeMs !== null) return `+${((row.timeMs - winner.timeMs) / 1000).toFixed(3)}`
  return row.statusDetail ?? row.status ?? ''
}

function raceTime(ms: number): string {
  const h = Math.floor(ms / 3_600_000)
  const m = Math.floor((ms % 3_600_000) / 60_000)
  const s = Math.floor((ms % 60_000) / 1000)
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(ms % 1000).padStart(3, '0')}`
}

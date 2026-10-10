import { useState } from 'react'
import { CartesianGrid, Legend, Line, LineChart, Tooltip, XAxis, YAxis } from 'recharts'
import { useLapTimes } from '../api/queries'
import type { LapTimes } from '../api/types'
import { RaceLeaf } from './RaceLeaf'

// /races/:year/:raceId/laps — lap pace: every lap time, driver by driver (OpenF1 for opened races,
// else the F1 history from 1996). The top five finishers are shown to begin with; slow laps (the start,
// pit stops, safety cars) run off the top of the chart so the racing pace stays readable.

const SOURCE: Record<LapTimes['source'], string> = {
  OPENF1: 'Lap times from OpenF1',
  JOLPICA: 'Lap times from the F1 history (Jolpica)',
  NONE: 'No lap times for this race (they start in 1996)',
}
const COLOURS = [
  '#ef4444',
  '#3b82f6',
  '#22c55e',
  '#eab308',
  '#a855f7',
  '#f97316',
  '#14b8a6',
  '#ec4899',
  '#84cc16',
  '#f4f4f5',
]

export function LapPacePage() {
  return <RaceLeaf title="Lap pace">{(raceId) => <LapPace raceId={raceId} />}</RaceLeaf>
}

function LapPace({ raceId }: { raceId: number }) {
  const laps = useLapTimes(raceId)
  const [chosen, setChosen] = useState<Set<number> | null>(null)
  if (laps.isPending) return <p className="text-zinc-400">Loading lap times…</p>
  if (laps.isError) return <p role="alert">{laps.error.message}</p>
  const { drivers, source } = laps.data
  const shown = chosen ?? new Set(drivers.slice(0, 5).map((d) => d.number))
  const name = (d: LapTimes['drivers'][number]) => d.code ?? d.surname ?? String(d.number)

  // one row per lap: { lap, [number]: seconds }
  const maxLap = Math.max(0, ...drivers.flatMap((d) => d.laps.map((l) => l.lap)))
  const rows = Array.from({ length: maxLap }, (_, i) => {
    const row: Record<string, number> = { lap: i + 1 }
    for (const d of drivers) {
      const lap = d.laps.find((l) => l.lap === i + 1)
      if (lap) row[d.number] = lap.ms / 1000
    }
    return row
  })
  const fastest = Math.min(...drivers.flatMap((d) => d.laps.map((l) => l.ms / 1000)))

  const toggle = (n: number) => {
    const next = new Set(shown)
    if (next.has(n)) next.delete(n)
    else next.add(n)
    setChosen(next)
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-zinc-400">{SOURCE[source]}</p>
      {drivers.length > 0 && (
        <>
          <fieldset className="flex flex-wrap gap-x-3 gap-y-1 text-sm">
            <legend className="sr-only">Drivers</legend>
            {drivers.map((d) => (
              <label key={d.number} className="flex items-center gap-1">
                <input
                  type="checkbox"
                  checked={shown.has(d.number)}
                  onChange={() => toggle(d.number)}
                  className="accent-red-600"
                />
                {name(d)}
              </label>
            ))}
          </fieldset>
          <div className="overflow-x-auto">
            <LineChart width={900} height={420} data={rows} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
              <CartesianGrid stroke="#27272a" />
              <XAxis dataKey="lap" stroke="#71717a" fontSize={11} />
              <YAxis
                stroke="#71717a"
                fontSize={11}
                width={56}
                domain={[Math.floor(fastest), Math.ceil(fastest * 1.12)]}
                allowDataOverflow
                tickFormatter={(s: number) => `${Math.floor(s / 60)}:${String(Math.round(s % 60)).padStart(2, '0')}`}
              />
              <Tooltip contentStyle={{ background: '#18181b', border: '1px solid #3f3f46' }} />
              <Legend />
              {drivers
                .filter((d) => shown.has(d.number))
                .map((d, i) => (
                  <Line
                    key={d.number}
                    dataKey={String(d.number)}
                    name={name(d)}
                    stroke={COLOURS[i % COLOURS.length]}
                    dot={false}
                    isAnimationActive={false}
                    connectNulls
                  />
                ))}
            </LineChart>
          </div>
        </>
      )}
    </div>
  )
}

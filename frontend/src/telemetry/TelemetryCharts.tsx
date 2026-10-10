import { CartesianGrid, Line, LineChart, ReferenceLine, Tooltip, XAxis, YAxis } from 'recharts'
import type { LapComparison } from '../api/types'
import type { Row } from './series'

// The telemetry traces against distance (Recharts), one chart per channel; a second lap overlaid in its
// driver's colour, and the delta between them. `cursor` (metres) marks where the car is on the replay's
// clock.

const WIDTH = 760
const CHANNELS: { key: keyof Row; other: keyof Row; title: string; unit: string; height: number; step?: boolean }[] = [
  { key: 'speed', other: 'otherSpeed', title: 'Speed', unit: 'km/h', height: 160 },
  { key: 'throttle', other: 'otherThrottle', title: 'Throttle', unit: '%', height: 90 },
  { key: 'brake', other: 'otherBrake', title: 'Brake', unit: '', height: 70, step: true },
  { key: 'gear', other: 'otherGear', title: 'Gear', unit: '', height: 90, step: true },
  { key: 'rpm', other: 'otherRpm', title: 'RPM', unit: 'rpm', height: 110 },
]

interface Props {
  rows: Row[]
  delta?: LapComparison['delta']
  colour: string
  otherColour?: string
  cursor?: number | null
}

export function TelemetryCharts({ rows, delta, colour, otherColour, cursor }: Props) {
  const maxDistance = rows.length ? rows[rows.length - 1].distance : 0
  const axis = (
    <XAxis
      dataKey="distance"
      type="number"
      domain={[0, maxDistance]}
      tickFormatter={(m: number) => `${(m / 1000).toFixed(1)} km`}
      stroke="#71717a"
      fontSize={11}
    />
  )
  const marker = cursor != null ? <ReferenceLine x={cursor} stroke="#f4f4f5" strokeDasharray="3 3" /> : null
  return (
    <div className="space-y-4 overflow-x-auto">
      {CHANNELS.map((c) => (
        <section key={c.title}>
          <div className="flex items-baseline gap-1 text-sm">
            <h3 className="font-semibold text-zinc-300">{c.title}</h3>
            {c.unit && <span className="text-zinc-500">({c.unit})</span>}
          </div>
          <LineChart width={WIDTH} height={c.height} data={rows} margin={{ top: 4, right: 12, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="#27272a" vertical={false} />
            {axis}
            <YAxis stroke="#71717a" fontSize={11} width={48} />
            <Tooltip
              contentStyle={{ background: '#18181b', border: '1px solid #3f3f46' }}
              labelFormatter={(m) => `${Math.round(Number(m))} m`}
            />
            <Line
              dataKey={c.key}
              name="reference"
              stroke={colour}
              dot={false}
              isAnimationActive={false}
              type={c.step ? 'stepAfter' : 'linear'}
            />
            {otherColour && (
              <Line
                dataKey={c.other}
                name="other"
                stroke={otherColour}
                dot={false}
                isAnimationActive={false}
                type={c.step ? 'stepAfter' : 'linear'}
              />
            )}
            {marker}
          </LineChart>
        </section>
      ))}
      {delta && (
        <section>
          <div className="flex items-baseline gap-1 text-sm">
            <h3 className="font-semibold text-zinc-300">Delta</h3>
            <span className="text-zinc-500">(s behind the reference lap)</span>
          </div>
          <LineChart width={WIDTH} height={140} data={delta} margin={{ top: 4, right: 12, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="#27272a" vertical={false} />
            {axis}
            <YAxis stroke="#71717a" fontSize={11} width={48} />
            <ReferenceLine y={0} stroke="#52525b" />
            <Tooltip contentStyle={{ background: '#18181b', border: '1px solid #3f3f46' }} />
            <Line dataKey="delta" stroke={otherColour} dot={false} isAnimationActive={false} />
            {marker}
          </LineChart>
        </section>
      )}
    </div>
  )
}

import { useEffect, useState, type ReactNode } from 'react'
import { useParams, useSearchParams } from 'react-router'
import { useLapAnalysis, useLapComparison, useRace, useRaceImport, useReplay } from '../api/queries'
import type { LapAnalysis, ReplayDriver } from '../api/types'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { TelemetryCharts } from '../telemetry/TelemetryCharts'
import { cursorDistance, formatDelta, formatLapTime, mergeLaps } from '../telemetry/series'
import { useWatch } from '../watch/useWatch'

// /races/:year/:raceId/telemetry?driver=VER&lap=39&vs=LEC&vsLap=36 — a lap's telemetry against distance
// (no lap: the driver's fastest), optionally against another driver's, with the delta and sector deltas.
// Fetched on demand: the first time a driver is asked for, the server downloads their telemetry. With
// ?watch=… the charts' cursor follows that watch-together session's clock.
export function TelemetryPage() {
  const params = useParams()
  const year = Number(params.year)
  const raceId = Number(params.raceId)
  const [search, setSearch] = useSearchParams()
  const race = useRace(raceId)
  const opened = useRaceImport(raceId)
  const session = race.data?.sessions.find((s) => s.name === 'Race')
  const replay = useReplay(opened.data?.state === 'READY' ? session?.id : undefined)
  const drivers = replay.data?.drivers ?? []

  const byCode = (code: string | null) => drivers.find((d) => d.code === code)
  const driver = byCode(search.get('driver')) ?? drivers[0]
  const vs = byCode(search.get('vs'))
  const lap = Number(search.get('lap')) || undefined
  const vsLap = Number(search.get('vsLap')) || undefined

  const single = useLapAnalysis(vs ? undefined : session?.id, driver?.number, lap)
  const compared = useLapComparison(session?.id, driver?.number, lap, vs?.number, vsLap)
  const reference = vs ? compared.data?.reference : single.data
  const other = vs ? compared.data?.other : undefined
  const pending = vs ? compared.isPending : single.isPending
  const error = (vs ? compared.error : single.error) ?? race.error ?? replay.error

  const cursor = useCursor(search.get('watch'), reference, replay.data?.clockStart)

  const choose = (key: 'driver' | 'vs', code: string) => {
    const next = new URLSearchParams(search)
    if (!search.get('driver') && driver) next.set('driver', driver.code)
    if (code) next.set(key, code)
    else next.delete(key)
    next.delete(key === 'driver' ? 'lap' : 'vsLap') // a different driver: their fastest lap
    setSearch(next)
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: 'Dashboard', to: '/dashboard' },
          { label: 'Grand Prix', to: '/races' },
          { label: String(year), to: `/races/${year}` },
          { label: race.data?.name ?? '…', to: `/races/${year}/${raceId}` },
          { label: 'Telemetry' },
        ]}
      />
      <h1 className="text-3xl font-bold">{race.data?.name ?? 'Telemetry'}</h1>

      {drivers.length > 0 && (
        <div className="flex flex-wrap items-center gap-4 text-sm">
          <DriverPicker
            label="Driver"
            drivers={drivers}
            value={driver?.code ?? ''}
            onChange={(c) => choose('driver', c)}
          />
          <DriverPicker
            label="Compare with"
            drivers={drivers.filter((d) => d.number !== driver?.number)}
            value={vs?.code ?? ''}
            onChange={(c) => choose('vs', c)}
            optional
          />
        </div>
      )}

      {error ? (
        <p role="alert" className="rounded-md border border-red-900 bg-red-950 p-4 text-red-200">
          {error.message}
        </p>
      ) : pending || !reference || !driver ? (
        <p className="text-zinc-400">Downloading telemetry… the first time for a driver takes a few seconds.</p>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <LapCard lap={reference} driver={driver} />
            {other && vs && <LapCard lap={other} driver={vs} />}
          </div>
          {other && compared.data && (
            <table aria-label="Sectors" className="w-full max-w-xl text-sm tabular-nums">
              <thead className="text-zinc-400">
                <tr>
                  <th className="text-left font-normal" />
                  <th className="text-right font-normal">{driver.code}</th>
                  <th className="text-right font-normal">{vs?.code}</th>
                  <th className="text-right font-normal">Δ</th>
                </tr>
              </thead>
              <tbody>
                {[0, 1, 2].map((i) => (
                  <tr key={i}>
                    <td>Sector {i + 1}</td>
                    <td className="text-right">{reference.sectors[i]?.toFixed(3) ?? '—'}</td>
                    <td className="text-right">{other.sectors[i]?.toFixed(3) ?? '—'}</td>
                    <td className="text-right">{formatDelta(compared.data.sectorDeltas[i])}</td>
                  </tr>
                ))}
                <tr className="font-semibold">
                  <td>Lap</td>
                  <td className="text-right">{formatLapTime(reference.lapTime)}</td>
                  <td className="text-right">{formatLapTime(other.lapTime)}</td>
                  <td className="text-right">{formatDelta(compared.data.lapDelta)}</td>
                </tr>
              </tbody>
            </table>
          )}
          <TelemetryCharts
            rows={mergeLaps(reference, other)}
            delta={compared.data && vs ? compared.data.delta : undefined}
            colour={colourOf(driver)}
            otherColour={vs ? colourOf(vs) : undefined}
            cursor={cursor}
          />
        </>
      )}
    </div>
  )
}

function LapCard({ lap, driver }: { lap: LapAnalysis; driver: ReplayDriver }) {
  return (
    <section
      aria-label={`${driver.code} lap ${lap.lap}`}
      className="rounded-xl border border-zinc-800 bg-zinc-900 p-4 text-sm"
    >
      <h2 className="flex items-baseline gap-2 text-lg font-semibold">
        <span className="h-3 w-3 rounded-full" style={{ backgroundColor: colourOf(driver) }} />
        {driver.code} · lap {lap.lap}
        <span className="ml-auto tabular-nums">{formatLapTime(lap.lapTime)}</span>
      </h2>
      <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-zinc-300">
        <Item label="Tyres">
          {lap.tyre
            ? `${lap.tyre.compound} · stint ${lap.tyre.stint} · ${lap.tyre.age} lap${lap.tyre.age === 1 ? '' : 's'} old`
            : '—'}
        </Item>
        <Item label="Fuel">
          {lap.estimates.fuelKg.toFixed(1)} kg <Estimate />
        </Item>
        <Item label="ERS">
          {fmt(lap.estimates.ersDeployedMj)} MJ deployed, {fmt(lap.estimates.ersHarvestedMj)} MJ harvested <Estimate />
        </Item>
      </dl>
      <p className="mt-2 text-xs text-zinc-500">{lap.estimates.note}</p>
    </section>
  )
}

function Item({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-zinc-500">{label}</dt>
      <dd>{children}</dd>
    </>
  )
}

function Estimate() {
  return <span className="text-xs text-amber-400">(estimate)</span>
}

function DriverPicker(props: {
  label: string
  drivers: ReplayDriver[]
  value: string
  onChange: (code: string) => void
  optional?: boolean
}) {
  return (
    <label className="flex items-center gap-2 text-zinc-400">
      {props.label}
      <select
        value={props.value}
        onChange={(e) => props.onChange(e.target.value)}
        className="rounded-md border border-zinc-700 bg-zinc-900 px-3 py-1.5 text-zinc-100"
      >
        {props.optional && <option value="">—</option>}
        {props.drivers.map((d) => (
          <option key={d.number} value={d.code}>
            {d.code}
          </option>
        ))}
      </select>
    </label>
  )
}

// the cursor follows the watch-together session's clock, when there is one
function useCursor(watchId: string | null, lap: LapAnalysis | undefined, clockStart: string | undefined) {
  const watch = useWatch(watchId)
  const [cursor, setCursor] = useState<number | null>(null)
  useEffect(() => {
    if (!watch || !lap || !clockStart) return
    const timer = setInterval(() => {
      const t = watch.sync.raceTime(Date.now())
      setCursor(t === null ? null : cursorDistance(lap, clockStart, t))
    }, 250)
    return () => clearInterval(timer)
  }, [watch, lap, clockStart])
  return watch ? cursor : null
}

function colourOf(driver: ReplayDriver): string {
  return driver.teamColour ? `#${driver.teamColour}` : '#a1a1aa'
}

function fmt(mj: number): string {
  return Number.isInteger(mj) ? String(mj) : mj.toFixed(2)
}

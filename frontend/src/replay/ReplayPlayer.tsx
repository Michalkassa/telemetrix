import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { useStore } from 'zustand'
import { scaleOrdinal } from 'd3-scale'
import type { Replay, Snapshot, TimelineEvent, WeatherReading } from '../api/types'
import { EventList } from '../timeline/EventList'
import { currentEvent } from '../timeline/timeline'
import type { WatchCommand } from '../watch/channel'
import type { ClockSync } from '../watch/sync'
import { Controls, type PlaybackControls } from './Controls'
import { drawFrame, HEIGHT, WIDTH } from './draw'
import {
  derivedAt,
  gapsAt,
  SECTOR_COLOURS,
  sectorColours,
  sectorTimes,
  weatherAt,
  type SectorColour,
  type SectorTime,
} from './overlays'
import { lapStart, leaderLap, prepare, runningOrder, type CarNow, type Race } from './race'
import { createPlayerStore, timeAt } from './store'

// tyre compounds as F1 colours them (D3 ordinal scale: one colour per compound)
const compoundColour = scaleOrdinal<string, string>()
  .domain(['SOFT', 'MEDIUM', 'HARD', 'INTERMEDIATE', 'WET'])
  .range(['#ef4444', '#facc15', '#f4f4f5', '#22c55e', '#3b82f6'])
  .unknown('#71717a')

/** Watching together: the shared clock replaces the local one; only the host has controls. */
export interface Watching {
  role: 'host' | 'viewer'
  sync: ClockSync
  send: (command: WatchCommand) => void
}

interface Props {
  replay: Replay
  // the derived state at every change (flags, yellow sectors, DRS, tyres, penalties): GET .../snapshots
  snapshots?: Snapshot[]
  // the race's events (GET /api/races/{id}/timeline): a list beside the player and markers on the bar
  events?: TimelineEvent[]
  watch?: Watching
  // the clocks (wall, for the local clock; epoch, for the shared one) and animation frames, replaceable in tests
  now?: () => number
  epochNow?: () => number
  requestFrame?: (cb: FrameRequestCallback) => number | void
  cancelFrame?: (id: number) => void
}

interface Row {
  number: number
  code: string
  colour: string
  position: number
  gap: string // to the car ahead
  tyre: string | null // compound letter and laps on them: "M12"
  tyreColour: string
  drs: boolean
  penalty: string | null // "+5s", "DT"
  inPit: boolean
  sectors: (SectorColour | null)[]
}

interface Hud {
  t: number
  eventTime: number // when the current event started (exact, unlike t): the event list's "now"
  lap: number
  order: CarNow[]
  rows: Row[]
  weather: WeatherReading | null
  key: string // what's shown, to tell whether it changed
}

// The race on a canvas (docs/adr/0004): a requestAnimationFrame loop reads the playback clock — the
// player's store, or the shared clock when watching together — and draws the track and one dot per car,
// through a camera that can follow one car or keep several in view. The lap counter, race time and
// running order are React state, updated only when they change, so the page isn't re-rendered 60 times a
// second.
export function ReplayPlayer({
  replay,
  snapshots = [],
  events = [],
  watch,
  now = () => performance.now(),
  epochNow = () => Date.now(),
  requestFrame = (cb) => requestAnimationFrame(cb),
  cancelFrame = (id) => cancelAnimationFrame(id),
}: Props) {
  const race = useMemo(() => prepare(replay), [replay])
  const sectors = useMemo(() => sectorTimes(race), [race])
  const store = useMemo(() => createPlayerStore(race.duration), [race])
  const localPlaying = useStore(store, (s) => s.playing)
  const localSpeed = useStore(store, (s) => s.speed)
  const follow = useStore(store, (s) => s.follow)
  const focus = useStore(store, (s) => s.focus)
  const canvas = useRef<HTMLCanvasElement>(null)
  const [hud, setHud] = useState<Hud>(() => hudAt(race, replay, snapshots, sectors, events, 0))
  const [shared, setShared] = useState({ playing: false, speed: 1 }) // the shared clock, as last drawn

  // the latest callbacks without restarting the loop
  const env = useRef({ now, epochNow, requestFrame, cancelFrame, watch, snapshots, events })
  useLayoutEffect(() => {
    env.current = { now, epochNow, requestFrame, cancelFrame, watch, snapshots, events }
  })

  useEffect(() => {
    const element = canvas.current
    const ctx = element?.getContext('2d')
    if (!element || !ctx) return
    let stopped = false
    let id: number | void

    const frame = () => {
      if (stopped) return
      const sync = env.current.watch?.sync
      let t: number
      if (sync) {
        t = clamp(sync.raceTime(env.current.epochNow()) ?? 0, 0, race.duration)
        const playing = Boolean(sync.state?.playing)
        const speed = sync.state?.speed ?? 1
        setShared((s) => (s.playing === playing && s.speed === speed ? s : { playing, speed }))
      } else {
        const state = store.getState()
        t = timeAt(state, env.current.now())
        if (state.playing && t >= race.duration) state.pause(env.current.now())
      }
      const shots = env.current.snapshots
      const next = hudAt(race, replay, shots, sectors, env.current.events, t)
      const { follow: followed, focus: focused } = store.getState()
      drawFrame(element, ctx, {
        race,
        replay,
        t,
        order: runningOrder(race, t),
        follow: followed,
        focus: focused,
        derived: derivedAt(shots, t),
        gaps: gapsAt(race, t),
      })
      setHud((shown) => (shown.key === next.key ? shown : next))
      id = env.current.requestFrame(frame)
    }
    id = env.current.requestFrame(frame)
    return () => {
      stopped = true
      if (typeof id === 'number') env.current.cancelFrame(id)
    }
  }, [race, replay, sectors, store])

  const playing = watch ? shared.playing : localPlaying
  const speed = watch ? shared.speed : localSpeed

  // the controls move the local clock, or — for the host — the shared one, through the server
  const controls: PlaybackControls = watch
    ? {
        toggle: () => watch.send({ action: playing ? 'PAUSE' : 'PLAY' }),
        seek: (ms) => watch.send({ action: 'SEEK', value: clamp(Math.round(ms), 0, race.duration) }),
        skip: (delta) => {
          const at = watch.sync.raceTime(epochNow()) ?? 0
          watch.send({ action: 'SEEK', value: clamp(Math.round(at + delta), 0, race.duration) })
        },
        setSpeed: (value) => watch.send({ action: 'SPEED', value }),
      }
    : {
        toggle: () => store.getState().toggle(now()),
        seek: (ms) => store.getState().seek(ms, now()),
        skip: (delta) => store.getState().skip(delta, now()),
        setSpeed: (value) => store.getState().setSpeed(value, now()),
      }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_21rem]">
      <div className="space-y-3">
        <canvas
          ref={canvas}
          width={WIDTH}
          height={HEIGHT}
          className="h-auto w-full rounded-xl border border-zinc-800 bg-zinc-900"
          aria-label="Track map with one dot per car"
        />
        <div className="flex items-center gap-4">
          <span data-testid="lap-counter" className="font-semibold tabular-nums">
            Lap {hud.lap} / {race.totalLaps}
          </span>
          <span data-testid="race-time" className="text-zinc-400 tabular-nums">
            {formatRaceTime(hud.t)}
          </span>
          {hud.weather && (
            <span data-testid="weather" className="ml-auto text-sm text-zinc-400 tabular-nums">
              {hud.weather.rainfall ? '🌧 Rain · ' : ''}
              {hud.weather.airTemperature !== null && `Air ${Math.round(hud.weather.airTemperature)}°`}
              {hud.weather.trackTemperature !== null && ` · Track ${Math.round(hud.weather.trackTemperature)}°`}
            </span>
          )}
          {watch?.role === 'viewer' && (
            <span className="rounded-full border border-zinc-700 px-4 py-2 text-sm text-zinc-300">
              {playing ? '▶' : '❚❚'} Following the host
            </span>
          )}
        </div>
        {watch?.role !== 'viewer' && (
          <Controls
            controls={controls}
            playing={playing}
            speed={speed}
            time={hud.t}
            duration={race.duration}
            totalLaps={race.totalLaps}
            lapStart={(lap) => lapStart(race, lap)}
            events={events}
          />
        )}
        {events.length > 0 && (
          // a viewer follows the host's clock: the list shows where the race is, but only the host seeks
          <EventList
            events={events}
            time={hud.eventTime}
            onSeek={watch?.role === 'viewer' ? undefined : controls.seek}
          />
        )}
      </div>
      <ol aria-label="Running order" className="divide-y divide-zinc-800 rounded-xl border border-zinc-800 text-sm">
        {hud.rows.map((car) => (
          <li key={car.number} className="flex items-center gap-2 px-3 py-1.5">
            <span className="w-5 text-right tabular-nums text-zinc-400">{car.position}</span>
            <span className="h-3 w-1 rounded-sm" style={{ backgroundColor: car.colour }} />
            <span className="w-9 font-semibold">{car.code}</span>
            <span className="w-14 text-right text-xs tabular-nums text-zinc-400">{car.gap}</span>
            {car.tyre && (
              <span className="w-8 text-xs font-semibold tabular-nums" style={{ color: car.tyreColour }}>
                {car.tyre}
              </span>
            )}
            <span className="flex gap-0.5" aria-hidden="true">
              {car.sectors.map((c, i) => (
                <span
                  key={i}
                  className="h-1.5 w-2 rounded-sm"
                  style={{ backgroundColor: c ? SECTOR_COLOURS[c] : '#27272a' }}
                />
              ))}
            </span>
            {car.drs && <span className="rounded bg-green-600 px-1 text-[10px] font-bold">DRS</span>}
            {car.inPit && <span className="rounded bg-zinc-700 px-1 text-[10px] font-bold">PIT</span>}
            {car.penalty && <span className="rounded bg-red-900 px-1 text-[10px] font-bold">{car.penalty}</span>}
            <span className="ml-auto flex gap-1">
              <button
                type="button"
                aria-label={`Follow ${car.code}`}
                aria-pressed={follow === car.number}
                title="Follow: keep this car in the centre"
                onClick={() => store.getState().toggleFollow(car.number)}
                className={`rounded px-1.5 ${follow === car.number ? 'bg-red-600 text-white' : 'text-zinc-500 hover:text-zinc-100'}`}
              >
                ◎
              </button>
              <button
                type="button"
                aria-label={`Focus ${car.code}`}
                aria-pressed={focus.includes(car.number)}
                title="Focus: keep these cars in view"
                onClick={() => store.getState().toggleFocus(car.number)}
                className={`rounded px-1.5 ${focus.includes(car.number) ? 'bg-zinc-100 text-zinc-900' : 'text-zinc-500 hover:text-zinc-100'}`}
              >
                ⊕
              </button>
            </span>
          </li>
        ))}
      </ol>
    </div>
  )
}

// What the page shows besides the canvas, at whole seconds (so it changes about once a second).
function hudAt(
  race: Race,
  replay: Replay,
  snapshots: Snapshot[],
  sectors: SectorTime[],
  events: TimelineEvent[],
  time: number,
): Hud {
  // the current event from the exact time: a seek lands on an event's own millisecond
  const eventTime = currentEvent(events, time)?.t ?? -Infinity
  const t = Math.floor(time / 1000) * 1000
  const order = runningOrder(race, t)
  const derived = derivedAt(snapshots, t)
  const gaps = gapsAt(race, t)
  const colours = sectorColours(sectors, t)
  const retired = new Set(
    replay.timing.filter((c) => !c.finished && c.retiredAt !== null && t >= c.retiredAt).map((c) => c.number),
  )
  const pitting = new Set(replay.timing.filter((c) => c.pits.some((p) => t >= p.from && t < p.to)).map((c) => c.number))
  const rows: Row[] = order.map((car) => {
    const state = derived?.cars[String(car.number)]
    const gap = gaps.get(car.number)
    const tyreAge = state?.stintLapStart != null ? Math.max(0, car.lap - state.stintLapStart + state.ageAtStart) : null
    const penalty =
      state && state.penaltySeconds > 0
        ? `+${state.penaltySeconds}s`
        : state && state.pendingPenalties > 0
          ? 'PEN'
          : null
    return {
      number: car.number,
      code: car.code,
      colour: car.colour,
      position: car.position,
      gap: retired.has(car.number)
        ? 'OUT'
        : !gap || gap.interval === null
          ? 'Leader'
          : gap.lapsDown > 0
            ? `+${gap.lapsDown} lap`
            : `+${gap.interval.toFixed(1)}`,
      tyre: state?.compound ? `${state.compound[0]}${tyreAge ?? ''}` : null,
      tyreColour: compoundColour(state?.compound ?? ''),
      drs: Boolean(derived?.drsEnabled && gap?.interval !== null && gap?.interval !== undefined && gap.interval <= 1),
      penalty,
      inPit: pitting.has(car.number),
      sectors: colours.get(car.number) ?? [null, null, null],
    }
  })
  const weather = weatherAt(replay.weather ?? [], t)
  return {
    t,
    eventTime,
    lap: leaderLap(race, t),
    order,
    rows,
    weather,
    key: JSON.stringify([t, eventTime, rows, weather]),
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function formatRaceTime(ms: number): string {
  const seconds = Math.floor(ms / 1000)
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = String(seconds % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
}

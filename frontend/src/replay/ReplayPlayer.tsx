import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { Replay } from '../api/types'
import { PlaybackClock } from './clock'
import { leaderLap, prepare, runningOrder, type CarNow, type Race } from './race'
import { fitTransform, pointAt } from './track'

// The canvas size in pixels; CSS scales it to the page width.
const WIDTH = 800
const HEIGHT = 600
const PADDING = 40
const DOT_RADIUS = 9

interface Props {
  replay: Replay
  // the wall clock and animation frames, replaceable in tests
  now?: () => number
  requestFrame?: (cb: FrameRequestCallback) => number | void
  cancelFrame?: (id: number) => void
}

interface Hud {
  t: number
  lap: number
  order: CarNow[]
}

// The race on a canvas (docs/adr/0004): a requestAnimationFrame loop reads the playback clock and draws
// the track and one dot per car. The lap counter and running order are React state, updated only when
// they change, so the page isn't re-rendered 60 times a second.
export function ReplayPlayer({
  replay,
  now = () => performance.now(),
  requestFrame = (cb) => requestAnimationFrame(cb),
  cancelFrame = (id) => cancelAnimationFrame(id),
}: Props) {
  const race = useMemo(() => prepare(replay), [replay])
  const clock = useMemo(() => new PlaybackClock({ end: race.duration }), [race])
  const canvas = useRef<HTMLCanvasElement>(null)
  const [playing, setPlaying] = useState(false)
  const [hud, setHud] = useState<Hud>(() => hudAt(race, 0))

  // the latest callbacks without restarting the loop
  const env = useRef({ now, requestFrame, cancelFrame })
  useLayoutEffect(() => {
    env.current = { now, requestFrame, cancelFrame }
  })

  useEffect(() => {
    const ctx = canvas.current?.getContext('2d')
    if (!ctx) return
    const toCanvas = race.track ? fitTransform(race.track.points, WIDTH, HEIGHT, { padding: PADDING }) : null
    let stopped = false
    let id: number | void

    const frame = () => {
      if (stopped) return
      const t = clock.timeAt(env.current.now())
      const next = hudAt(race, t)
      draw(ctx, race, next.order, toCanvas)
      setHud((shown) => (sameHud(shown, next) ? shown : next))
      if (clock.playing && t >= clock.end) {
        clock.pause(env.current.now())
        setPlaying(false)
      }
      id = env.current.requestFrame(frame)
    }
    id = env.current.requestFrame(frame)
    return () => {
      stopped = true
      if (typeof id === 'number') env.current.cancelFrame(id)
    }
  }, [race, clock])

  const toggle = () => {
    if (clock.playing) clock.pause(now())
    else clock.play(now())
    setPlaying(clock.playing)
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_14rem]">
      <div className="space-y-3">
        <canvas
          ref={canvas}
          width={WIDTH}
          height={HEIGHT}
          className="h-auto w-full rounded-xl border border-zinc-800 bg-zinc-900"
          aria-label="Track map with one dot per car"
        />
        <div className="flex items-center gap-4">
          <button
            type="button"
            onClick={toggle}
            className="w-24 rounded-full bg-red-600 px-4 py-2 font-semibold text-white hover:bg-red-500"
          >
            {playing ? 'Pause' : 'Play'}
          </button>
          <span data-testid="lap-counter" className="font-semibold tabular-nums">
            Lap {hud.lap} / {race.totalLaps}
          </span>
          <span data-testid="race-time" className="text-zinc-400 tabular-nums">
            {formatRaceTime(hud.t)}
          </span>
        </div>
      </div>
      <ol aria-label="Running order" className="divide-y divide-zinc-800 rounded-xl border border-zinc-800 text-sm">
        {hud.order.map((car) => (
          <li key={car.number} className="flex items-center gap-3 px-3 py-1.5">
            <span className="w-5 text-right tabular-nums text-zinc-400">{car.position}</span>
            <span className="h-3 w-1 rounded-sm" style={{ backgroundColor: car.colour }} />
            <span className="font-semibold">{car.code}</span>
          </li>
        ))}
      </ol>
    </div>
  )
}

// What the page shows besides the canvas, at whole seconds (so it changes about once a second).
function hudAt(race: Race, t: number): Hud {
  return { t: Math.floor(t / 1000) * 1000, lap: leaderLap(race, t), order: runningOrder(race, t) }
}

function sameHud(a: Hud, b: Hud): boolean {
  return (
    a.t === b.t &&
    a.lap === b.lap &&
    a.order.length === b.order.length &&
    a.order.every((car, i) => car.number === b.order[i].number)
  )
}

function draw(
  ctx: CanvasRenderingContext2D,
  race: Race,
  order: CarNow[],
  toCanvas: ((x: number, y: number) => [number, number]) | null,
) {
  ctx.clearRect(0, 0, WIDTH, HEIGHT)
  if (!race.track || !toCanvas) return

  ctx.beginPath()
  race.track.points.forEach(([x, y], i) => {
    const [cx, cy] = toCanvas(x, y)
    if (i === 0) ctx.moveTo(cx, cy)
    else ctx.lineTo(cx, cy)
  })
  ctx.closePath()
  ctx.strokeStyle = '#52525b'
  ctx.lineWidth = 6
  ctx.stroke()

  // the leader last, so it's on top
  ctx.font = 'bold 9px system-ui, sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  for (let i = order.length - 1; i >= 0; i--) {
    const car = order[i]
    const [cx, cy] = toCanvas(...pointAt(race.track, car.lapsDone))
    ctx.beginPath()
    ctx.arc(cx, cy, DOT_RADIUS, 0, 2 * Math.PI)
    ctx.fillStyle = car.colour
    ctx.fill()
    ctx.fillStyle = '#ffffff'
    ctx.fillText(String(car.number), cx, cy)
  }
}

function formatRaceTime(ms: number): string {
  const seconds = Math.floor(ms / 1000)
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = String(seconds % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
}

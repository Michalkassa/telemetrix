import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { TimelineEvent } from '../api/types'
import { markers } from '../timeline/timeline'
import { SPEEDS } from './store'

// The player's controls: play / pause, back and forward 10 s, speed (0.25–8×), jump to a lap or a race
// time, and the timeline bar to scrub. Keyboard: space plays / pauses, ← → skip 10 s, ↑ ↓ change speed
// (not while typing in a box). The actions go to the local clock, or to the shared one when hosting.

export interface PlaybackControls {
  toggle(): void
  seek(ms: number): void
  skip(deltaMs: number): void
  setSpeed(speed: number): void
}

const SKIP_MS = 10_000

interface Props {
  controls: PlaybackControls
  playing: boolean
  speed: number
  time: number // race time shown, ms
  duration: number
  totalLaps: number
  lapStart: (lap: number) => number // when the leader started a lap, ms
  events?: TimelineEvent[] // the race's events, marked on the timeline bar
}

const MARKER_COLOUR: Record<string, string> = {
  SAFETY_CAR: 'bg-amber-400/70',
  VSC: 'bg-amber-300/50',
  RED_FLAG: 'bg-red-600/80',
  CRASH: 'bg-red-500',
  DNF: 'bg-red-400',
  INCIDENT: 'bg-orange-400',
  PENALTY: 'bg-fuchsia-400',
  WEATHER: 'bg-sky-400',
}

export function Controls({ controls, playing, speed, time, duration, totalLaps, lapStart, events = [] }: Props) {
  const [lap, setLap] = useState('')
  const [at, setAt] = useState('')

  // the latest values for the keyboard handler, without re-subscribing every render
  const latest = useRef({ controls, speed })
  useEffect(() => {
    latest.current = { controls, speed }
  })

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null
      if (target && (target.closest('input, textarea, select') || target.isContentEditable)) return
      const { controls: c, speed: s } = latest.current
      const i = SPEEDS.indexOf(s)
      switch (e.key) {
        case ' ':
          c.toggle()
          break
        case 'ArrowLeft':
          c.skip(-SKIP_MS)
          break
        case 'ArrowRight':
          c.skip(SKIP_MS)
          break
        case 'ArrowUp':
          if (i < SPEEDS.length - 1) c.setSpeed(SPEEDS[i + 1])
          break
        case 'ArrowDown':
          if (i > 0) c.setSpeed(SPEEDS[i - 1])
          break
        default:
          return
      }
      e.preventDefault() // no page scrolling
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const jumpToLap = (e: FormEvent) => {
    e.preventDefault()
    const n = Number(lap)
    if (Number.isInteger(n) && n >= 1 && n <= totalLaps) controls.seek(lapStart(n))
  }

  const jumpToTime = (e: FormEvent) => {
    e.preventDefault()
    const ms = parseRaceTime(at)
    if (ms !== null) controls.seek(ms)
  }

  const box = 'rounded-md border border-zinc-700 bg-zinc-900 px-2 py-1 text-sm text-zinc-100'
  const round = 'rounded-full border border-zinc-700 px-3 py-1.5 text-sm hover:bg-zinc-800'
  return (
    <div className="space-y-3">
      <div className="relative">
        {/* the race's key moments over the bar: bands for the safety car / VSC / red flag */}
        <div className="pointer-events-none absolute inset-x-0 -top-3 h-3">
          {markers(events, duration).map(({ event, left, width }, i) => (
            <button
              key={`${event.type}-${event.t}-${i}`}
              type="button"
              aria-label={`Marker: ${event.summary}${event.lap !== null ? `, lap ${event.lap}` : ''}`}
              title={event.summary}
              onClick={() => controls.seek(event.t)}
              className={`pointer-events-auto absolute top-0 h-3 rounded-sm ${MARKER_COLOUR[event.type] ?? 'bg-zinc-400'}`}
              style={{ left: `${left}%`, width: width > 0 ? `${width}%` : '3px' }}
            />
          ))}
        </div>
        <input
          type="range"
          aria-label="Race time"
          min={0}
          max={duration}
          step={100}
          value={Math.round(time)}
          onChange={(e) => controls.seek(Number(e.target.value))}
          className="w-full accent-red-600"
        />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <button type="button" onClick={() => controls.skip(-SKIP_MS)} aria-label="Back 10 s" className={round}>
          ⏪ 10 s
        </button>
        <button
          type="button"
          onClick={() => controls.toggle()}
          className="w-24 rounded-full bg-red-600 px-4 py-2 font-semibold text-white hover:bg-red-500"
        >
          {playing ? 'Pause' : 'Play'}
        </button>
        <button type="button" onClick={() => controls.skip(SKIP_MS)} aria-label="Forward 10 s" className={round}>
          10 s ⏩
        </button>
        <fieldset className="flex items-center gap-1 text-sm">
          <legend className="sr-only">Speed</legend>
          {SPEEDS.map((s) => (
            <label
              key={s}
              className={`cursor-pointer rounded-md px-2 py-1 ${s === speed ? 'bg-zinc-100 text-zinc-900' : 'text-zinc-400 hover:text-zinc-100'}`}
            >
              <input
                type="radio"
                name="speed"
                aria-label={`${s}×`}
                checked={s === speed}
                onChange={() => controls.setSpeed(s)}
                className="sr-only"
              />
              {s}×
            </label>
          ))}
        </fieldset>
        <form onSubmit={jumpToLap} className="flex items-center gap-1">
          <input
            aria-label="Jump to lap"
            inputMode="numeric"
            placeholder="lap"
            value={lap}
            onChange={(e) => setLap(e.target.value)}
            className={`${box} w-16`}
          />
        </form>
        <form onSubmit={jumpToTime} className="flex items-center gap-1">
          <input
            aria-label="Jump to time"
            placeholder="m:ss"
            value={at}
            onChange={(e) => setAt(e.target.value)}
            className={`${box} w-20`}
          />
        </form>
      </div>
    </div>
  )
}

/** "1:05" or "1:02:03" (race time) → ms; null if it isn't one. */
function parseRaceTime(text: string): number | null {
  const parts = text.trim().split(':')
  if (parts.length < 2 || parts.length > 3 || parts.some((p) => !/^\d+$/.test(p))) return null
  const [h, m, s] = parts.length === 3 ? parts.map(Number) : [0, ...parts.map(Number)]
  if (s >= 60 || (parts.length === 3 && m >= 60)) return null
  return ((h * 60 + m) * 60 + s) * 1000
}

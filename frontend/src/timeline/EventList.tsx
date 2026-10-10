import { useEffect, useMemo, useRef, useState } from 'react'
import type { TimelineEvent } from '../api/types'
import { currentEvent, filterEvents } from './timeline'

// The race's events (Step 22) as a list beside the player: click one to seek there; the one happening
// now is highlighted (and kept in view while playing); checkboxes hide types.

const EVENT_TYPES: [string, string][] = [
  ['CHAMPIONSHIP_LEAD', 'Championship lead'],
  ['SAFETY_CAR', 'Safety car'],
  ['VSC', 'VSC'],
  ['RED_FLAG', 'Red flags'],
  ['YELLOW_FLAG', 'Yellow flags'],
  ['CRASH', 'Crashes'],
  ['INCIDENT', 'Incidents'],
  ['DNF', 'DNFs'],
  ['PENALTY', 'Penalties'],
  ['OVERTAKE', 'Overtakes'],
  ['PIT_STOP', 'Pit stops'],
  ['TYRE_CHANGE', 'Tyre changes'],
  ['FASTEST_LAP', 'Fastest laps'],
  ['WEATHER', 'Weather'],
  ['TEAM_RADIO', 'Team radio'],
]

const DOT: Record<TimelineEvent['severity'], string> = {
  HIGH: 'bg-red-500',
  MEDIUM: 'bg-amber-400',
  LOW: 'bg-zinc-500',
}

interface Props {
  events: TimelineEvent[]
  time: number // race time now, ms
  onSeek?: (t: number) => void // none: read-only (a watch-together viewer)
}

export function EventList({ events, time, onSeek }: Props) {
  const [hidden, setHidden] = useState<Set<string>>(new Set())
  const shown = useMemo(() => filterEvents(events, hidden), [events, hidden])
  const current = currentEvent(shown, time)
  const present = new Set(events.map((e) => e.type))

  // keep the current event in view as the race plays (only within the list's own scroll box)
  const currentItem = useRef<HTMLLIElement>(null)
  useEffect(() => {
    currentItem.current?.scrollIntoView?.({ block: 'nearest' })
  }, [current])

  const toggle = (type: string) =>
    setHidden((h) => {
      const next = new Set(h)
      if (next.has(type)) next.delete(type)
      else next.add(type)
      return next
    })

  return (
    <section className="space-y-2">
      <h2 className="text-lg font-semibold">Race events</h2>
      <fieldset className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-zinc-400">
        <legend className="sr-only">Show</legend>
        {EVENT_TYPES.filter(([type]) => present.has(type)).map(([type, label]) => (
          <label key={type} className="flex items-center gap-1">
            <input
              type="checkbox"
              checked={!hidden.has(type)}
              onChange={() => toggle(type)}
              className="accent-red-600"
            />
            {label}
          </label>
        ))}
      </fieldset>
      <ol
        aria-label="Race events"
        className="max-h-96 divide-y divide-zinc-800 overflow-y-auto rounded-xl border border-zinc-800 text-sm"
      >
        {shown.map((event, i) => {
          const isCurrent = event === current
          return (
            <li
              key={`${event.type}-${event.t}-${i}`}
              ref={isCurrent ? currentItem : undefined}
              aria-current={isCurrent ? 'true' : undefined}
              className={`flex items-center gap-2 px-3 py-1.5 ${isCurrent ? 'bg-zinc-800' : ''}`}
            >
              <button
                type="button"
                onClick={() => onSeek?.(event.t)}
                disabled={!onSeek}
                className="flex min-w-0 flex-1 items-center gap-2 text-left hover:text-red-400"
              >
                <span className={`h-2 w-2 shrink-0 rounded-full ${DOT[event.severity]}`} aria-hidden="true" />
                <span className="truncate">{event.summary}</span>
                <span className="ml-auto shrink-0 text-xs tabular-nums text-zinc-500">
                  {event.lap !== null && `L${event.lap} · `}
                  {raceClock(event.t)}
                </span>
              </button>
              {event.link && (
                <a
                  href={event.link}
                  target="_blank"
                  rel="noopener"
                  className="text-xs text-zinc-400 hover:text-zinc-100"
                >
                  Listen
                </a>
              )}
            </li>
          )
        })}
      </ol>
    </section>
  )
}

function raceClock(ms: number): string {
  const sign = ms < 0 ? '−' : ''
  const s = Math.floor(Math.abs(ms) / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const ss = String(s % 60).padStart(2, '0')
  return `${sign}${h > 0 ? `${h}:${String(m).padStart(2, '0')}` : m}:${ss}`
}

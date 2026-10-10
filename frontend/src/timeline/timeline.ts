import type { TimelineEvent } from '../api/types'

// The race timeline (GET /api/races/{id}/timeline) for the player: the event happening now, filters,
// and where events sit on the scrub bar.

const SEVERITY = { LOW: 0, MEDIUM: 1, HIGH: 2 }

/** The latest event started at or before `t` (events in time order); at the same moment, the most important. */
export function currentEvent(events: TimelineEvent[], t: number): TimelineEvent | null {
  let current: TimelineEvent | null = null
  for (const event of events) {
    if (event.t > t) break
    if (!current || event.t > current.t || SEVERITY[event.severity] > SEVERITY[current.severity]) current = event
  }
  return current
}

export function filterEvents(events: TimelineEvent[], hidden: Set<string>): TimelineEvent[] {
  return hidden.size === 0 ? events : events.filter((e) => !hidden.has(e.type))
}

// what earns a marker on the scrub bar (overtakes, pit stops and radio would bury the rest)
const MARKED = new Set(['SAFETY_CAR', 'VSC', 'RED_FLAG', 'CRASH', 'INCIDENT', 'DNF', 'PENALTY', 'WEATHER'])
const BANDS = new Set(['SAFETY_CAR', 'VSC', 'RED_FLAG'])

export interface Marker {
  event: TimelineEvent
  left: number // % of the bar
  width: number // % of the bar; 0 for a moment
}

/** The scrub bar's markers: moments, and bands for the safety car, VSC and red flags. */
export function markers(events: TimelineEvent[], duration: number): Marker[] {
  if (duration <= 0) return []
  const percent = (t: number) => Math.min(Math.max((t / duration) * 100, 0), 100)
  return events
    .filter((e) => MARKED.has(e.type) && e.t >= 0 && e.t <= duration)
    .map((event) => {
      const ends =
        BANDS.has(event.type) && event.endsAt ? event.t + (Date.parse(event.endsAt) - Date.parse(event.at)) : event.t
      return { event, left: percent(event.t), width: percent(ends) - percent(event.t) }
    })
}

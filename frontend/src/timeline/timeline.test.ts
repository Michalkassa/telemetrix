import { describe, expect, test } from 'vitest'
import type { Timeline } from '../api/types'
import recorded from '../test/fixtures/singapore2023/timeline.json'
import { currentEvent, filterEvents, markers } from './timeline'

// Singapore 2023's timeline as the API serves it (recorded): every expected value comes from it.
const timeline = recorded as Timeline
const events = timeline.events
const sc = events.find((e) => e.type === 'SAFETY_CAR')!
const vsc = events.find((e) => e.type === 'VSC')!
const span = (endsAt: string) => Date.parse(endsAt) - Date.parse(timeline.clockStart)

describe('currentEvent', () => {
  test('the latest event that has started by the clock', () => {
    const latestBefore = events.filter((e) => e.t <= sc.t + 1000).at(-1)

    expect(currentEvent(events, sc.t + 1000)).toBe(latestBefore)
    expect(currentEvent(events, sc.t)?.t).toBe(sc.t)
  })

  test('none before the first event', () => {
    expect(currentEvent(events, events[0].t - 1)).toBeNull()
  })

  test('at the same moment, the most important event', () => {
    const same = [
      { ...sc, type: 'TEAM_RADIO', severity: 'LOW' as const },
      sc,
      { ...sc, type: 'PIT_STOP', severity: 'LOW' as const },
    ]
    expect(currentEvent(same, sc.t)).toBe(sc)
  })
})

describe('filterEvents', () => {
  test('hides the types switched off', () => {
    const shown = filterEvents(events, new Set(['OVERTAKE', 'TEAM_RADIO']))

    expect(shown).toHaveLength(events.filter((e) => e.type !== 'OVERTAKE' && e.type !== 'TEAM_RADIO').length)
    expect(shown.some((e) => e.type === 'OVERTAKE')).toBe(false)
    expect(filterEvents(events, new Set())).toBe(events)
  })
})

describe('markers', () => {
  const duration = Math.max(...events.map((e) => e.t))
  const placed = markers(events, duration)

  test('the safety car and the VSC as bands, from start to end', () => {
    const scBand = placed.find((m) => m.event === sc)!
    expect(scBand.left).toBeCloseTo((sc.t / duration) * 100, 9)
    expect(scBand.width).toBeCloseTo(((span(sc.endsAt!) - sc.t) / duration) * 100, 9)
    expect(placed.find((m) => m.event === vsc)?.width).toBeGreaterThan(0)
  })

  test('only the moments that matter, within the race', () => {
    const types = new Set(placed.map((m) => m.event.type))
    expect(types.has('OVERTAKE')).toBe(false)
    expect(types.has('TEAM_RADIO')).toBe(false)
    expect(types.has('DNF')).toBe(true)
    expect(types.has('INCIDENT')).toBe(true)
    expect(placed.every((m) => m.left >= 0 && m.left <= 100)).toBe(true)
  })
})

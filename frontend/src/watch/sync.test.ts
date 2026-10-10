import { describe, expect, test } from 'vitest'
import { ClockSync, raceTimeAt, type ClockState } from './sync'

const at = Date.parse('2026-10-09T20:00:00Z')

function state(over: Partial<ClockState>): ClockState {
  return {
    watchId: 'w1',
    sessionId: 5,
    seq: 1,
    playing: false,
    speed: 1,
    positionMs: 0,
    at: new Date(at).toISOString(),
    serverTime: new Date(at).toISOString(),
    ...over,
  }
}

describe('raceTimeAt', () => {
  test('paused: the position, however much later', () => {
    expect(raceTimeAt(state({ positionMs: 90_000 }), at + 60_000)).toBe(90_000)
  })

  test('playing: the position plus server time since, times the speed', () => {
    expect(raceTimeAt(state({ playing: true, positionMs: 90_000, speed: 4 }), at + 2_000)).toBe(98_000)
  })
})

describe('ClockSync', () => {
  test("works out the server's time from ours", () => {
    const sync = new ClockSync()
    // the server is 5 s ahead of us; the message took 30 ms to arrive
    sync.observe(state({ serverTime: new Date(at).toISOString() }), at - 5_000 + 30)

    expect(sync.serverNow(at - 5_000 + 1_000)).toBe(at + 1_000 - 30)
  })

  test('the quickest message gives the best estimate (delays only make the server look behind)', () => {
    const sync = new ClockSync()
    sync.observe(state({ seq: 1, serverTime: new Date(at).toISOString() }), at + 200)
    sync.observe(state({ seq: 2, serverTime: new Date(at + 1_000).toISOString() }), at + 1_010)

    expect(sync.serverNow(at + 2_000)).toBe(at + 1_990)
  })

  test('keeps the latest state, ignoring any that arrive out of order', () => {
    const sync = new ClockSync()
    expect(sync.observe(state({ seq: 2, positionMs: 2_000 }), at)).toBe(true)
    expect(sync.observe(state({ seq: 1, positionMs: 1_000 }), at)).toBe(false)

    expect(sync.state?.positionMs).toBe(2_000)
  })

  test('the race time now, on our clock', () => {
    const sync = new ClockSync()
    sync.observe(state({ playing: true, positionMs: 10_000, serverTime: new Date(at).toISOString() }), at)

    expect(sync.raceTime(at + 3_000)).toBe(13_000)
    expect(new ClockSync().raceTime(at)).toBeNull()
  })
})

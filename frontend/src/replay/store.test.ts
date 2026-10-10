import { describe, expect, test } from 'vitest'
import { createPlayerStore, SPEEDS, timeAt } from './store'

// The player's state (Zustand): the playback clock and who's followed / in focus. Wall time (`now`, ms)
// is passed in, so nothing waits.
function store(duration = 60_000) {
  return createPlayerStore(duration)
}

describe('playing', () => {
  test('starts paused at the start, at 1×, following no one', () => {
    const s = store().getState()
    expect(s).toMatchObject({ playing: false, speed: 1, follow: null, focus: [] })
    expect(timeAt(s, 5_000)).toBe(0)
  })

  test('play: race time moves on with wall time × speed; pause freezes it', () => {
    const s = store()
    s.getState().play(1_000)
    expect(timeAt(s.getState(), 3_000)).toBe(2_000)

    s.getState().pause(3_000)
    expect(s.getState().playing).toBe(false)
    expect(timeAt(s.getState(), 10_000)).toBe(2_000)
  })

  test('toggle plays or pauses', () => {
    const s = store()
    s.getState().toggle(0)
    expect(s.getState().playing).toBe(true)
    s.getState().toggle(500)
    expect(s.getState().playing).toBe(false)
    expect(timeAt(s.getState(), 999)).toBe(500)
  })

  test('stops at the end', () => {
    const s = store(60_000)
    s.getState().play(0)
    expect(timeAt(s.getState(), 100_000)).toBe(60_000)
  })
})

describe('moving around', () => {
  test('seek jumps, and playing carries on from there', () => {
    const s = store()
    s.getState().play(0)
    s.getState().seek(30_000, 1_000)
    expect(timeAt(s.getState(), 2_000)).toBe(31_000)
  })

  test('seek stays within the race', () => {
    const s = store(60_000)
    s.getState().seek(-5, 0)
    expect(timeAt(s.getState(), 0)).toBe(0)
    s.getState().seek(99_000, 0)
    expect(timeAt(s.getState(), 0)).toBe(60_000)
  })

  test('rewind and fast forward skip 10 s', () => {
    const s = store()
    s.getState().seek(30_000, 0)
    s.getState().skip(10_000, 0)
    expect(timeAt(s.getState(), 0)).toBe(40_000)
    s.getState().skip(-10_000, 0)
    s.getState().skip(-10_000, 0)
    expect(timeAt(s.getState(), 0)).toBe(20_000)
  })
})

describe('speed', () => {
  test('0.25× to 8×', () => {
    expect(SPEEDS).toEqual([0.25, 0.5, 1, 2, 4, 8])
  })

  test('a new speed keeps the position, then goes faster', () => {
    const s = store()
    s.getState().play(0)
    s.getState().setSpeed(4, 1_000)
    expect(timeAt(s.getState(), 1_000)).toBe(1_000)
    expect(timeAt(s.getState(), 2_000)).toBe(5_000)
  })

  test('faster and slower step through the speeds, stopping at the ends', () => {
    const s = store()
    s.getState().faster(0)
    expect(s.getState().speed).toBe(2)
    s.getState().setSpeed(8, 0)
    s.getState().faster(0)
    expect(s.getState().speed).toBe(8)
    s.getState().setSpeed(0.25, 0)
    s.getState().slower(0)
    expect(s.getState().speed).toBe(0.25)
    s.getState().faster(0)
    expect(s.getState().speed).toBe(0.5)
  })

  test('an unknown speed is ignored', () => {
    const s = store()
    s.getState().setSpeed(3, 0)
    expect(s.getState().speed).toBe(1)
  })
})

describe('following and focus', () => {
  test('follow a driver; following them again stops it', () => {
    const s = store()
    s.getState().toggleFollow(44)
    expect(s.getState().follow).toBe(44)
    s.getState().toggleFollow(1)
    expect(s.getState().follow).toBe(1)
    s.getState().toggleFollow(1)
    expect(s.getState().follow).toBeNull()
  })

  test('a set of drivers in focus', () => {
    const s = store()
    s.getState().toggleFocus(1)
    s.getState().toggleFocus(44)
    expect(s.getState().focus).toEqual([1, 44])
    s.getState().toggleFocus(1)
    expect(s.getState().focus).toEqual([44])
    s.getState().clearFocus()
    expect(s.getState().focus).toEqual([])
  })
})

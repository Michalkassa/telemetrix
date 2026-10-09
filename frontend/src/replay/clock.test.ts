import { describe, expect, test } from 'vitest'
import { PlaybackClock } from './clock'

// The wall clock is passed in (like a java.time.Clock on the server), so nothing here sleeps.
describe('PlaybackClock', () => {
  test('starts paused at the start', () => {
    const clock = new PlaybackClock({ end: 60_000 })
    expect(clock.playing).toBe(false)
    expect(clock.timeAt(5_000)).toBe(0)
  })

  test('while playing, race time advances by the wall time elapsed', () => {
    const clock = new PlaybackClock({ end: 60_000 })
    clock.play(1_000)
    expect(clock.timeAt(3_500)).toBe(2_500)
  })

  test('advances by elapsed × speed', () => {
    const clock = new PlaybackClock({ end: 60_000, speed: 4 })
    clock.play(1_000)
    expect(clock.timeAt(2_000)).toBe(4_000)
  })

  test('pause freezes the race time, and play carries on from there', () => {
    const clock = new PlaybackClock({ end: 60_000 })
    clock.play(0)
    clock.pause(2_000)
    expect(clock.playing).toBe(false)
    expect(clock.timeAt(9_000)).toBe(2_000)

    clock.play(10_000)
    expect(clock.timeAt(11_000)).toBe(3_000)
  })

  test('pressing play twice changes nothing', () => {
    const clock = new PlaybackClock({ end: 60_000 })
    clock.play(0)
    clock.play(5_000)
    expect(clock.timeAt(6_000)).toBe(6_000)
  })

  test('stops at the end', () => {
    const clock = new PlaybackClock({ end: 60_000, speed: 8 })
    clock.play(0)
    expect(clock.timeAt(60_000)).toBe(60_000)
  })
})

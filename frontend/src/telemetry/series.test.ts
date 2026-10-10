import { describe, expect, test } from 'vitest'
import type { LapAnalysis, TracePoint } from '../api/types'
import { cursorDistance, formatLapTime, formatDelta, mergeLaps } from './series'

// Turning the API's laps into chart rows.

function point(time: number, distance: number, speed: number): TracePoint {
  return { time, distance, speed, throttle: 100, brake: 0, gear: 7, rpm: 11_000, drs: 0 }
}

function lap(over: Partial<LapAnalysis>): LapAnalysis {
  return {
    sessionId: 5,
    driver: 1,
    lap: 39,
    startsAt: '2024-03-02T16:05:28.544Z',
    lapTime: 10,
    sectors: [3, 4, 3],
    tyre: null,
    estimates: { fuelKg: 36.7, ersDeployedMj: 4, ersHarvestedMj: 2, note: 'Estimates' },
    distance: 500,
    trace: [point(0, 0, 180), point(5_000, 250, 180), point(10_000, 500, 180)],
    ...over,
  }
}

describe('mergeLaps', () => {
  test('one lap: a row per point, by distance', () => {
    expect(mergeLaps(lap({}))).toEqual([
      { distance: 0, speed: 180, throttle: 100, brake: 0, gear: 7, rpm: 11_000 },
      { distance: 250, speed: 180, throttle: 100, brake: 0, gear: 7, rpm: 11_000 },
      { distance: 500, speed: 180, throttle: 100, brake: 0, gear: 7, rpm: 11_000 },
    ])
  })

  test("two laps: the other's values at the same share of its lap, on the reference's distances", () => {
    const other = lap({
      driver: 16,
      distance: 600,
      trace: [point(0, 0, 100), point(6_000, 300, 200), point(12_000, 600, 300)],
    })

    const rows = mergeLaps(lap({}), other)

    expect(rows.map((r) => [r.distance, r.speed, r.otherSpeed])).toEqual([
      [0, 180, 100],
      [250, 180, 200], // halfway round both laps
      [500, 180, 300],
    ])
  })
})

describe('formatting', () => {
  test('lap times and deltas', () => {
    expect(formatLapTime(92.608)).toBe('1:32.608')
    expect(formatLapTime(59.5)).toBe('0:59.500')
    expect(formatDelta(0.322)).toBe('+0.322')
    expect(formatDelta(-1.05)).toBe('−1.050')
    expect(formatDelta(null)).toBe('—')
  })
})

describe('cursorDistance', () => {
  // the lap started at 16:05:28.544; the replay clock's zero was 15:03:42.341
  const clockStart = '2024-03-02T15:03:42.341Z'
  const lapStartsAt = Date.parse('2024-03-02T16:05:28.544Z') - Date.parse(clockStart)

  test("where the car is in this lap at a race time (the replay's clock)", () => {
    expect(cursorDistance(lap({}), clockStart, lapStartsAt + 5_000)).toBe(250)
    expect(cursorDistance(lap({}), clockStart, lapStartsAt + 2_500)).toBe(125)
  })

  test('nowhere when the clock is outside the lap', () => {
    expect(cursorDistance(lap({}), clockStart, lapStartsAt - 1)).toBeNull()
    expect(cursorDistance(lap({}), clockStart, lapStartsAt + 10_001)).toBeNull()
  })
})

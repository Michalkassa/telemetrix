import { describe, expect, test } from 'vitest'
import type { Snapshot } from '../api/types'
import { squareRace } from '../test/fixtures/replay'
import {
  type SectorTime,
  banner,
  derivedAt,
  gapsAt,
  pathPointAt,
  safetyCarProgress,
  sectorColours,
  sectorTimes,
  trackColour,
  weatherAt,
  yellowSegments,
} from './overlays'
import { prepare } from './race'

// What the overlays show, worked out from the race state. Drawing is elsewhere; these are plain data.

describe('sector times', () => {
  test('from each complete lap’s four timing points', () => {
    const race = prepare({
      ...squareRace,
      timing: [
        // lap 1: sectors of 30, 40 and 30 s; lap 2 only start and end (a missing sector): no sectors
        {
          number: 1,
          t: [0, 30_000, 70_000, 100_000, 190_000],
          laps: [0, 0.25, 0.75, 1, 2],
          pits: [],
          finished: true,
          retiredAt: null,
        },
      ],
    })

    expect(sectorTimes(race)).toEqual([
      { car: 1, lap: 1, sector: 1, endsAt: 30_000, ms: 30_000 },
      { car: 1, lap: 1, sector: 2, endsAt: 70_000, ms: 40_000 },
      { car: 1, lap: 1, sector: 3, endsAt: 100_000, ms: 30_000 },
    ])
  })
})

describe('sector colours', () => {
  const times: SectorTime[] = [
    { car: 1, lap: 1, sector: 1, endsAt: 30_000, ms: 30_000 }, // first ever: purple
    { car: 44, lap: 1, sector: 1, endsAt: 31_000, ms: 30_500 }, // slower, but HAM's best: green
    { car: 1, lap: 2, sector: 1, endsAt: 130_000, ms: 30_200 }, // slower than VER's own: yellow
    { car: 44, lap: 2, sector: 1, endsAt: 131_000, ms: 29_900 }, // fastest of all: purple
  ]

  test('purple: fastest of all so far; green: own best; yellow: neither', () => {
    expect(sectorColours(times, 31_000).get(1)).toEqual(['purple', null, null])
    expect(sectorColours(times, 31_000).get(44)).toEqual(['green', null, null])
    expect(sectorColours(times, 140_000).get(1)).toEqual(['yellow', null, null])
    expect(sectorColours(times, 140_000).get(44)).toEqual(['purple', null, null])
  })

  test('only what has happened by then', () => {
    expect(sectorColours(times, 29_999).size).toBe(0)
  })
})

describe('flags', () => {
  test('a banner for the safety car, the VSC and a red flag', () => {
    expect(banner('SAFETY_CAR')).toEqual({ text: 'SAFETY CAR', colour: '#facc15' })
    expect(banner('VSC')).toEqual({ text: 'VIRTUAL SAFETY CAR', colour: '#facc15' })
    expect(banner('VSC_ENDING')).toEqual({ text: 'VSC ENDING', colour: '#facc15' })
    expect(banner('RED')).toEqual({ text: 'RED FLAG', colour: '#ef4444' })
    expect(banner('GREEN')).toBeNull()
    expect(banner('YELLOW')).toBeNull() // shown on the sectors instead
  })

  test('the whole track takes the colour of a neutralised race', () => {
    expect(trackColour('GREEN')).toBe('#52525b')
    expect(trackColour('YELLOW')).toBe('#52525b')
    expect(trackColour('SAFETY_CAR')).toBe('#a16207')
    expect(trackColour('RED')).toBe('#991b1b')
  })

  test('yellow sectors become stretches of the lap, from one marshal sector to the next', () => {
    const sectors = [
      { number: 1, progress: 0.99 }, // starts just before the line: runs across it
      { number: 2, progress: 0.1 },
      { number: 3, progress: 0.4 },
    ]

    expect(yellowSegments(sectors, { '2': 'YELLOW', '1': 'DOUBLE_YELLOW' })).toEqual([
      { from: 0.99, to: 1.1, double: true },
      { from: 0.1, to: 0.4, double: false },
    ])
    expect(yellowSegments(sectors, { '3': 'YELLOW' })).toEqual([{ from: 0.4, to: 0.99, double: false }])
    expect(yellowSegments([], { '3': 'YELLOW' })).toEqual([])
  })

  test('the safety car runs just ahead of the leader', () => {
    expect(safetyCarProgress(20.5)).toBeCloseTo(20.52, 9)
  })
})

describe('snapshots and weather', () => {
  const snap = (t: number, flag: Snapshot['flag']): Snapshot => ({
    t,
    lap: 1,
    reason: 'CHANGE',
    flag,
    yellowSectors: {},
    drsEnabled: false,
    cars: {},
  })

  test('the derived state at a moment is the latest snapshot at or before it', () => {
    const snapshots = [snap(0, 'GREEN'), snap(1_000, 'SAFETY_CAR'), snap(5_000, 'GREEN')]

    expect(derivedAt(snapshots, -1)).toBeNull()
    expect(derivedAt(snapshots, 1_000)?.flag).toBe('SAFETY_CAR')
    expect(derivedAt(snapshots, 4_999)?.flag).toBe('SAFETY_CAR')
    expect(derivedAt(snapshots, 9_000)?.flag).toBe('GREEN')
  })

  test('the weather is the latest reading', () => {
    const readings = [
      { t: -60_000, airTemperature: 30, trackTemperature: 38, humidity: 68, rainfall: false, windSpeed: 1 },
      { t: 600_000, airTemperature: 28, trackTemperature: 33, humidity: 80, rainfall: true, windSpeed: 2 },
    ]
    expect(weatherAt(readings, -70_000)).toBeNull()
    expect(weatherAt(readings, 0)?.trackTemperature).toBe(38)
    expect(weatherAt(readings, 700_000)?.rainfall).toBe(true)
  })
})

describe('gaps', () => {
  test('to the leader and the car ahead, in seconds, and laps down', () => {
    const race = prepare(squareRace)
    // at 6 s VER has 1.5 laps (reached 0.75 at 3 s); HAM has 0.75
    const gaps = gapsAt(race, 6_000)

    expect(gaps.get(1)).toEqual({ gap: 0, interval: null, lapsDown: 0 })
    expect(gaps.get(44)).toEqual({ gap: 3, interval: 3, lapsDown: 0 })
  })

  test('a car more than a lap behind is laps down', () => {
    const race = prepare({
      ...squareRace,
      timing: [
        { number: 1, t: [0, 1000], laps: [0, 10], pits: [], finished: true, retiredAt: null },
        { number: 44, t: [0, 1000], laps: [0, 8], pits: [], finished: true, retiredAt: null },
      ],
    })
    expect(gapsAt(race, 500).get(44)?.lapsDown).toBe(1)
  })
})

describe('the pit lane', () => {
  test('a point a share of the way along an open path, by distance', () => {
    const path: [number, number][] = [
      [0, 0],
      [100, 0],
      [100, 300],
    ]
    expect(pathPointAt(path, 0)).toEqual([0, 0])
    expect(pathPointAt(path, 0.5)).toEqual([100, 100])
    expect(pathPointAt(path, 1)).toEqual([100, 300])
    expect(pathPointAt(path, 2)).toEqual([100, 300])
  })
})

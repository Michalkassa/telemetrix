import { describe, expect, test } from 'vitest'
import { squareRace } from '../test/fixtures/replay'
import { lapStart, leaderLap, prepare, runningOrder } from './race'

const race = prepare(squareRace)

describe('runningOrder', () => {
  test('on the grid, cars keep the order of the timing (by number)', () => {
    expect(runningOrder(race, 0).map((c) => c.code)).toEqual(['VER', 'HAM'])
  })

  test('furthest round the race first, with each car on its current lap', () => {
    const order = runningOrder(race, 6000) // VER 1.5 laps done, HAM 0.75
    expect(order.map((c) => [c.position, c.code, c.lap])).toEqual([
      [1, 'VER', 2],
      [2, 'HAM', 1],
    ])
    expect(order[0].lapsDone).toBeCloseTo(1.5, 9)
    expect(order[1].lapsDone).toBeCloseTo(0.75, 9)
  })

  test("carries each driver's team colour", () => {
    expect(runningOrder(race, 0)[0].colour).toBe('#3671c6')
  })
})

describe('leaderLap', () => {
  test.each([
    [0, 1],
    [3999, 1],
    [4000, 2],
    [12000, 3], // the chequered flag: never past the last lap
  ])('at %d ms the leader is on lap %d', (t, lap) => {
    expect(leaderLap(race, t)).toBe(lap)
  })
})

describe('lapStart', () => {
  test('when the leader started a lap', () => {
    expect(lapStart(race, 1)).toBe(0)
    expect(lapStart(race, 2)).toBe(4000) // VER completes lap 1 at 4 s
    expect(lapStart(race, 3)).toBe(8000)
  })

  test('never past the last lap', () => {
    expect(lapStart(race, 99)).toBe(8000)
    expect(lapStart(race, 0)).toBe(0)
  })
})

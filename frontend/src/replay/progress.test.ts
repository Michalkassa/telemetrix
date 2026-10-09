import { describe, expect, test } from 'vitest'
import contract from '../../../contracts/progress-timeline.json'
import { progressAt, timeline } from './progress'

// The progressAt cases shared with the backend's ProgressTimelineContractTest: the browser and the server
// read a car's progress exactly the same way. (reachedAt is server-only for now.)
type Case = {
  name: string
  points: number[][]
  progressAt: number[][]
  reachedAt: (number | null)[][]
}

describe.each((contract.cases as Case[]).map((c) => [c.name, c] as const))('%s', (_name, c) => {
  const car = timeline(
    c.points.map((p) => p[0]),
    c.points.map((p) => p[1]),
  )

  test.each(c.progressAt.map(([t, laps]) => [t, laps]))('progressAt(%d) = %d', (t, laps) => {
    expect(progressAt(car, t)).toBeCloseTo(laps, 9)
  })
})

describe('progressAt', () => {
  test('a car with no timing points is on the grid', () => {
    expect(progressAt(timeline([], []), 1000)).toBe(0)
  })

  test('never runs backwards, even when the data does', () => {
    const car = timeline([0, 1000, 2000, 3000], [0, 0.5, 0.4, 1])
    const samples = [0, 500, 1000, 1500, 2000, 2500, 3000].map((t) => progressAt(car, t))
    expect(samples).toEqual([...samples].sort((a, b) => a - b))
  })
})

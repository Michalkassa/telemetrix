import { describe, expect, test } from 'vitest'
import { fitTransform, outline, pointAt } from './track'

// A 10 × 10 square, anticlockwise from the start/finish line at (0, 0): 40 long.
const square = outline([
  [0, 0],
  [10, 0],
  [10, 10],
  [0, 10],
])

describe('pointAt', () => {
  test.each([
    [0, [0, 0]],
    [0.125, [5, 0]],
    [0.25, [10, 0]],
    [0.5, [10, 10]],
    [0.875, [0, 5]], // on the stretch that closes the loop
  ])('progress %d is at %j, by distance round the lap', (progress, expected) => {
    const [x, y] = pointAt(square, progress)
    expect(x).toBeCloseTo(expected[0], 9)
    expect(y).toBeCloseTo(expected[1], 9)
  })

  test('ignores whole laps', () => {
    expect(pointAt(square, 3.125)).toEqual(pointAt(square, 0.125))
  })

  test('measures by distance, not by point count', () => {
    const uneven = outline([
      [0, 0],
      [1, 0],
      [2, 0],
      [10, 0],
      [10, 10],
      [0, 10],
    ])
    expect(pointAt(uneven, 0.125)).toEqual([5, 0])
  })
})

describe('fitTransform', () => {
  const box = [
    [0, 0],
    [100, 0],
    [100, 100],
    [0, 100],
  ] as [number, number][]

  test('fits the track in the canvas, centred, keeping its aspect ratio', () => {
    const toCanvas = fitTransform(box, 400, 200, { padding: 0 })
    // a square in a wide canvas: as tall as the canvas, centred across
    expect(toCanvas(0, 0)).toEqual([100, 200])
    expect(toCanvas(100, 100)).toEqual([300, 0])
  })

  test('north is up: world y grows upwards, canvas y downwards', () => {
    const toCanvas = fitTransform(box, 100, 100, { padding: 0 })
    expect(toCanvas(0, 100)[1]).toBeLessThan(toCanvas(0, 0)[1])
  })

  test('leaves the padding free on every side', () => {
    const toCanvas = fitTransform(box, 120, 120, { padding: 10 })
    expect(toCanvas(0, 0)).toEqual([10, 110])
    expect(toCanvas(100, 100)).toEqual([110, 10])
  })

  test('rotates anticlockwise before fitting', () => {
    const wide = [
      [0, 0],
      [200, 0],
      [200, 100],
      [0, 100],
    ] as [number, number][]
    // turned 90°, the 200 × 100 track stands upright and fills a 100 × 200 canvas exactly
    const toCanvas = fitTransform(wide, 100, 200, { padding: 0, rotation: 90 })
    const [x, y] = toCanvas(200, 0) // -> (0, 200) in the world: the top
    expect(x).toBeCloseTo(100, 9)
    expect(y).toBeCloseTo(0, 9)
  })
})

import { describe, expect, test } from 'vitest'
import { camera, FOLLOW_ZOOM, MAX_ZOOM } from './camera'
import { fitTransform, type Point } from './track'

// A 1000 × 1000 square track in an 800 × 600 canvas with 40 px padding: the whole track is 520 px tall.
const track: Point[] = [
  [0, 0],
  [1000, 0],
  [1000, 1000],
  [0, 1000],
]
const view = { width: 800, height: 600, padding: 40 }

const distance = (a: Point, b: Point) => Math.hypot(a[0] - b[0], a[1] - b[1])

describe('camera', () => {
  test('with nobody followed or in focus: the whole track', () => {
    const toCanvas = camera(track, view, {})
    const whole = fitTransform(track, 800, 600, { padding: 40 })

    expect(toCanvas(1000, 1000)).toEqual(whole(1000, 1000))
    expect(toCanvas(0, 0)).toEqual(whole(0, 0))
  })

  test('following a car: it stays in the centre, zoomed in', () => {
    const toCanvas = camera(track, view, { follow: [250, 700] })

    const [x, y] = toCanvas(250, 700)
    expect(x).toBeCloseTo(400, 9)
    expect(y).toBeCloseTo(300, 9)
    // 100 m is 52 px on the whole track; zoomed in, FOLLOW_ZOOM times that
    expect(distance(toCanvas(250, 700), toCanvas(350, 700))).toBeCloseTo(52 * FOLLOW_ZOOM, 6)
  })

  test('cars in focus: all in view, as large as fits, centred', () => {
    const toCanvas = camera(track, view, {
      focus: [
        [100, 100],
        [500, 300],
      ],
    })

    const [ax, ay] = toCanvas(100, 100)
    const [bx, by] = toCanvas(500, 300)
    expect(Math.min(ax, bx)).toBeCloseTo(40, 6) // 400 m wide fills the 720 px between the paddings
    expect(Math.max(ax, bx)).toBeCloseTo(760, 6)
    expect((ay + by) / 2).toBeCloseTo(300, 6)
    expect(ay).toBeGreaterThan(by) // north still up
  })

  test("cars close together don't zoom in without limit", () => {
    const toCanvas = camera(track, view, {
      focus: [
        [500, 500],
        [501, 500],
      ],
    })

    expect(distance(toCanvas(500, 500), toCanvas(600, 500))).toBeCloseTo(52 * MAX_ZOOM, 6)
  })

  test('following a car as well as a focus keeps it in the picture', () => {
    const toCanvas = camera(track, view, {
      focus: [
        [100, 100],
        [200, 100],
      ],
      follow: [900, 100],
    })

    const [x] = toCanvas(900, 100)
    expect(x).toBeLessThanOrEqual(760 + 1e-9)
    expect(toCanvas(100, 100)[0]).toBeCloseTo(40, 6)
  })
})

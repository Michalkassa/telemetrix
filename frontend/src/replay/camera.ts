import { fitTransform, type Point } from './track'

// Where the replay's camera points: world (track map) → canvas pixels, north up.
// - nobody followed or in focus: the whole track;
// - following a car: that car in the centre, FOLLOW_ZOOM times closer than the whole track;
// - cars in focus (plus the followed car, if any): the smallest view that holds them all, centred —
//   but never more than MAX_ZOOM times closer, so two cars side by side don't fill the screen.

export const FOLLOW_ZOOM = 3
export const MAX_ZOOM = 4

export function camera(
  track: Point[],
  view: { width: number; height: number; padding: number },
  shot: { follow?: Point | null; focus?: Point[] },
): (x: number, y: number) => Point {
  const { width, height, padding } = view
  const focus = shot.focus ?? []
  if (focus.length === 0 && !shot.follow) return fitTransform(track, width, height, { padding })

  const whole = scaleToFit(track, width - 2 * padding, height - 2 * padding)
  let centre: Point
  let scale: number
  if (focus.length > 0) {
    const points = shot.follow ? [...focus, shot.follow] : focus
    const box = bounds(points)
    centre = [(box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2]
    scale = Math.min(scaleToFit(points, width - 2 * padding, height - 2 * padding), whole * MAX_ZOOM)
  } else {
    centre = shot.follow!
    scale = whole * FOLLOW_ZOOM
  }
  return (x, y) => [width / 2 + (x - centre[0]) * scale, height / 2 - (y - centre[1]) * scale]
}

function scaleToFit(points: Point[], width: number, height: number): number {
  const box = bounds(points)
  const sx = box.maxX > box.minX ? width / (box.maxX - box.minX) : Infinity
  const sy = box.maxY > box.minY ? height / (box.maxY - box.minY) : Infinity
  return Math.min(sx, sy)
}

function bounds(points: Point[]) {
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const [x, y] of points) {
    minX = Math.min(minX, x)
    minY = Math.min(minY, y)
    maxX = Math.max(maxX, x)
    maxY = Math.max(maxY, y)
  }
  return { minX, minY, maxX, maxY }
}

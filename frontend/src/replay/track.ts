// The track map: the outline as a closed loop measured by distance (like the server's TrackOutline),
// and the world → canvas transform.

export type Point = [number, number]

export interface Outline {
  points: Point[]
  distanceAt: number[] // distance from the start to each point
  length: number // including the closing stretch back to the start
}

export function outline(points: Point[]): Outline {
  const distanceAt = [0]
  for (let i = 1; i < points.length; i++) {
    distanceAt.push(distanceAt[i - 1] + distance(points[i - 1], points[i]))
  }
  const length = distanceAt[points.length - 1] + distance(points[points.length - 1], points[0])
  return { points, distanceAt, length }
}

/** The point at a share of the lap distance (0 = the start/finish line); whole laps are ignored. */
export function pointAt(track: Outline, progress: number): Point {
  const { points, distanceAt, length } = track
  const wanted = (progress - Math.floor(progress)) * length
  // binary search: the last point at or before `wanted`
  let lo = 0
  let hi = points.length - 1
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1
    if (distanceAt[mid] <= wanted) lo = mid
    else hi = mid - 1
  }
  const from = points[lo]
  const to = points[(lo + 1) % points.length]
  const segment = (lo === points.length - 1 ? length : distanceAt[lo + 1]) - distanceAt[lo]
  const t = segment === 0 ? 0 : (wanted - distanceAt[lo]) / segment
  return [from[0] + t * (to[0] - from[0]), from[1] + t * (to[1] - from[1])]
}

/**
 * World → canvas pixels: rotates the points `rotation` degrees anticlockwise, then scales them to
 * fit inside the canvas less `padding`, centred, with the aspect ratio kept and north up.
 */
export function fitTransform(
  points: Point[],
  width: number,
  height: number,
  { padding, rotation = 0 }: { padding: number; rotation?: number },
): (x: number, y: number) => Point {
  const a = (rotation * Math.PI) / 180
  const cos = Math.cos(a)
  const sin = Math.sin(a)
  const rotate = (x: number, y: number): Point => (rotation === 0 ? [x, y] : [x * cos - y * sin, x * sin + y * cos])

  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const [px, py] of points) {
    const [x, y] = rotate(px, py)
    minX = Math.min(minX, x)
    minY = Math.min(minY, y)
    maxX = Math.max(maxX, x)
    maxY = Math.max(maxY, y)
  }
  const spanX = maxX - minX || 1
  const spanY = maxY - minY || 1
  const scale = Math.min((width - 2 * padding) / spanX, (height - 2 * padding) / spanY)
  const offsetX = (width - spanX * scale) / 2
  const offsetY = (height - spanY * scale) / 2

  return (px, py) => {
    const [x, y] = rotate(px, py)
    return [offsetX + (x - minX) * scale, height - offsetY - (y - minY) * scale]
  }
}

function distance(a: Point, b: Point): number {
  return Math.hypot(b[0] - a[0], b[1] - a[1])
}

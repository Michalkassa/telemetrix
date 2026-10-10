import { scaleLinear } from 'd3-scale'
import type { Replay, Snapshot } from '../api/types'
import { camera } from './camera'
import { banner, pathPointAt, safetyCarProgress, trackColour, type Gap, yellowSegments } from './overlays'
import type { CarNow, Race } from './race'
import { pointAt, type Outline, type Point } from './track'

// Drawing one frame of the replay on the canvas: the track (coloured by the flag), yellow marshalling
// sectors, sector markers, the pit lane, the safety car, one dot per car, gap labels for the cars being
// watched, the banner, and a mini-map of the whole track while the camera is zoomed in. What it shows
// is worked out in overlays.ts; this only paints it.

export const WIDTH = 800
export const HEIGHT = 600
const PADDING = 40
const DOT_RADIUS = 9
const MINI = { x: 12, y: HEIGHT - 132, width: 160, height: 120 }

export interface Scene {
  race: Race
  replay: Replay
  t: number
  order: CarNow[]
  follow: number | null
  focus: number[]
  derived: Snapshot | null
  gaps: Map<number, Gap>
}

export function drawFrame(canvas: HTMLCanvasElement, ctx: CanvasRenderingContext2D, scene: Scene) {
  const { race, replay, t, order, follow, focus, derived, gaps } = scene
  ctx.clearRect(0, 0, WIDTH, HEIGHT)
  const flag = derived?.flag ?? 'GREEN'
  const yellow = yellowSegments(replay.layout?.marshalSectors ?? [], derived?.yellowSectors ?? {})
  setData(canvas, 'banner', banner(flag)?.text ?? null)
  setData(
    canvas,
    'yellow',
    Object.keys(derived?.yellowSectors ?? {})
      .sort((a, b) => Number(a) - Number(b))
      .join(',') || null,
  )
  if (!race.track) return
  const track = race.track

  // where each car is: on the track, or along the pit lane while it's in it
  const pitLane = replay.layout?.pitLane ?? []
  const at = new Map<number, Point>()
  for (const car of order) {
    const stop = pitLane.length > 1 ? pitStopAt(replay, car.number, t) : null
    at.set(car.number, stop ? pathPointAt(pitLane, stop) : pointAt(track, car.lapsDone))
  }
  const toCanvas = camera(
    track.points,
    { width: WIDTH, height: HEIGHT, padding: PADDING },
    {
      follow: follow !== null ? (at.get(follow) ?? null) : null,
      focus: focus.map((car) => at.get(car)).filter((p): p is Point => p !== undefined),
    },
  )

  // the pit lane, then the track coloured by the flag, then the yellow stretches over it
  if (pitLane.length > 1)
    stroke(
      ctx,
      pitLane.map(([x, y]) => toCanvas(x, y)),
      '#3f3f46',
      3,
      false,
    )
  stroke(
    ctx,
    track.points.map(([x, y]) => toCanvas(x, y)),
    trackColour(flag),
    6,
    true,
  )
  for (const segment of yellow) {
    stroke(
      ctx,
      stretch(track, segment.from, segment.to).map(([x, y]) => toCanvas(x, y)),
      segment.double ? '#f97316' : '#facc15',
      8,
      false,
    )
  }

  // the start/finish line and the ends of sectors 1 and 2
  const marks = [0, ...(replay.layout?.sectorEnds ?? [])]
  marks.forEach((progress, i) => {
    const [x, y] = toCanvas(...pointAt(track, progress))
    ctx.fillStyle = i === 0 ? '#f4f4f5' : '#a1a1aa'
    ctx.fillRect(x - 2, y - 2, 4, 4)
    ctx.font = '10px system-ui, sans-serif'
    ctx.textAlign = 'left'
    ctx.textBaseline = 'bottom'
    ctx.fillText(i === 0 ? 'S/F' : `S${i + 1}`, x + 5, y - 3)
  })

  // the safety car, just ahead of the leader
  if (flag === 'SAFETY_CAR' && order.length > 0) {
    const [x, y] = toCanvas(...pointAt(track, safetyCarProgress(order[0].lapsDone)))
    dot(ctx, x, y, '#f4f4f5', 'SC', '#18181b')
  }

  // the cars, leader last (on top)
  for (let i = order.length - 1; i >= 0; i--) {
    const car = order[i]
    const [x, y] = toCanvas(...at.get(car.number)!)
    dot(ctx, x, y, car.colour, String(car.number), '#ffffff')
  }

  // gap labels for the cars being watched
  for (const number of new Set([...(follow !== null ? [follow] : []), ...focus])) {
    const car = order.find((c) => c.number === number)
    const gap = gaps.get(number)
    if (!car || !gap) continue
    const [x, y] = toCanvas(...at.get(number)!)
    ctx.font = 'bold 11px system-ui, sans-serif'
    ctx.textAlign = 'left'
    ctx.textBaseline = 'middle'
    ctx.fillStyle = '#f4f4f5'
    ctx.fillText(`${car.code} ${gap.interval === null ? 'P1' : `+${gap.interval.toFixed(1)}`}`, x + DOT_RADIUS + 4, y)
  }

  // the banner
  const top = banner(flag)
  if (top) {
    ctx.fillStyle = top.colour
    ctx.fillRect(0, 0, WIDTH, 34)
    ctx.fillStyle = '#18181b'
    ctx.font = 'bold 16px system-ui, sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(top.text, WIDTH / 2, 17)
  }

  // zoomed in: a mini-map of the whole track, every car on it
  if (follow !== null || focus.length > 0) miniMap(ctx, track, order, at)

  // where the followed car was drawn (for tests and tools; it should be the centre)
  const followed = follow !== null ? at.get(follow) : undefined
  setData(canvas, 'followed-at', followed ? toCanvas(...followed).join(',') : null)
}

// how far through a pit stop a car is at t (0–1), or null when it isn't in the pit lane
function pitStopAt(replay: Replay, car: number, t: number): number | null {
  const stop = replay.timing.find((c) => c.number === car)?.pits.find((p) => t >= p.from && t < p.to)
  return stop ? (t - stop.from) / (stop.to - stop.from) : null
}

// the outline from one share of the lap to another (`to` past 1 runs across the line)
function stretch(track: Outline, from: number, to: number): Point[] {
  const steps = Math.max(2, Math.ceil((to - from) * 200))
  return Array.from({ length: steps + 1 }, (_, i) => pointAt(track, from + ((to - from) * i) / steps))
}

function stroke(ctx: CanvasRenderingContext2D, points: Point[], colour: string, width: number, closed: boolean) {
  ctx.beginPath()
  points.forEach(([x, y], i) => (i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)))
  if (closed) ctx.closePath()
  ctx.strokeStyle = colour
  ctx.lineWidth = width
  ctx.stroke()
}

function dot(ctx: CanvasRenderingContext2D, x: number, y: number, colour: string, label: string, ink: string) {
  ctx.beginPath()
  ctx.arc(x, y, DOT_RADIUS, 0, 2 * Math.PI)
  ctx.fillStyle = colour
  ctx.fill()
  ctx.font = 'bold 9px system-ui, sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = ink
  ctx.fillText(label, x, y)
}

function miniMap(ctx: CanvasRenderingContext2D, track: Outline, order: CarNow[], at: Map<number, Point>) {
  const xs = track.points.map((p) => p[0])
  const ys = track.points.map((p) => p[1])
  const [minX, maxX, minY, maxY] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)]
  const scale = Math.min((MINI.width - 16) / (maxX - minX || 1), (MINI.height - 16) / (maxY - minY || 1))
  const cx = MINI.x + MINI.width / 2
  const cy = MINI.y + MINI.height / 2
  const x = scaleLinear()
    .domain([minX, maxX])
    .range([cx - ((maxX - minX) * scale) / 2, cx + ((maxX - minX) * scale) / 2])
  const y = scaleLinear()
    .domain([minY, maxY])
    .range([cy + ((maxY - minY) * scale) / 2, cy - ((maxY - minY) * scale) / 2]) // north up

  ctx.fillStyle = 'rgba(9, 9, 11, 0.85)'
  ctx.fillRect(MINI.x, MINI.y, MINI.width, MINI.height)
  stroke(
    ctx,
    track.points.map(([px, py]) => [x(px), y(py)]),
    '#71717a',
    2,
    true,
  )
  for (const car of order) {
    const [px, py] = at.get(car.number)!
    ctx.fillStyle = car.colour
    ctx.fillRect(x(px) - 2, y(py) - 2, 4, 4)
  }
}

function setData(canvas: HTMLCanvasElement, name: string, value: string | null) {
  if (value === null) canvas.removeAttribute(`data-${name}`)
  else canvas.setAttribute(`data-${name}`, value)
}

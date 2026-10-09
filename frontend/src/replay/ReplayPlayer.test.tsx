import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { squareRace } from '../test/fixtures/replay'
import { ReplayPlayer } from './ReplayPlayer'

// jsdom has no canvas: a fake 2D context records what is drawn. The wall clock and the animation
// frames are driven by the test, so nothing waits.
let ctx: Record<string, ReturnType<typeof vi.fn>>
let wall: number
let frames: FrameRequestCallback[]

beforeEach(() => {
  ctx = Object.fromEntries(
    ['clearRect', 'beginPath', 'moveTo', 'lineTo', 'closePath', 'stroke', 'arc', 'fill', 'fillText'].map((name) => [
      name,
      vi.fn(),
    ]),
  )
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as unknown as CanvasRenderingContext2D)
  wall = 0
  frames = []
})

afterEach(() => vi.restoreAllMocks())

function renderPlayer() {
  return render(
    <ReplayPlayer replay={squareRace} now={() => wall} requestFrame={(cb) => frames.push(cb)} cancelFrame={() => {}} />,
  )
}

// Moves the wall clock on and runs the animation frames waiting for it.
function tick(ms: number) {
  wall += ms
  const pending = frames
  frames = []
  act(() => pending.forEach((cb) => cb(wall)))
}

// The centres of the dots drawn since the last check: [x, y] per arc.
function dots() {
  const centres = ctx.arc.mock.calls.map(([x, y]) => [Math.round(x as number), Math.round(y as number)])
  ctx.arc.mockClear()
  return centres
}

// The 1000 × 1000 square in the 800 × 600 canvas with 40 px padding: scale 0.52, the line at (140, 560).
test('draws the track and one dot per car, on the grid before the start', () => {
  renderPlayer()
  tick(0)

  expect(ctx.lineTo).toHaveBeenCalled() // the outline
  expect(dots()).toEqual([
    [140, 560],
    [140, 560],
  ])
  expect(screen.getByTestId('lap-counter')).toHaveTextContent('Lap 1 / 3')
})

test('play moves the dots along the track; pause freezes them', async () => {
  renderPlayer()
  tick(0)
  dots()

  await userEvent.click(screen.getByRole('button', { name: 'Play' }))
  tick(1000) // VER a quarter round (1000, 0), HAM an eighth (500, 0) — the leader is drawn last, on top
  expect(dots()).toEqual([
    [400, 560],
    [660, 560],
  ])

  await userEvent.click(screen.getByRole('button', { name: 'Pause' }))
  tick(5000)
  expect(dots()).toEqual([
    [400, 560],
    [660, 560],
  ])
  expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()
})

test('the lap counter and the running order follow the race', async () => {
  renderPlayer()
  await userEvent.click(screen.getByRole('button', { name: 'Play' }))
  tick(6000) // VER 1.5 laps done, HAM 0.75

  expect(screen.getByTestId('lap-counter')).toHaveTextContent('Lap 2 / 3')
  expect(screen.getByTestId('race-time')).toHaveTextContent('0:06')
  const rows = screen.getAllByRole('listitem').map((li) => li.textContent)
  expect(rows).toEqual([expect.stringMatching(/^1\s*VER/), expect.stringMatching(/^2\s*HAM/)])
})

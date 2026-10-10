import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { openWatch } from '../watch/channel'
import { bahrain2024 } from '../test/fixtures/api'
import { ready, squareRace } from '../test/fixtures/replay'
import { renderRoute } from '../test/render'
import { server } from '../test/server'

// /races/:year/:raceId/replay: opening the race downloads its data once, then the player loads the
// Race session's replay.
vi.mock('../watch/channel', () => ({
  openWatch: vi.fn(() => ({ send: vi.fn(), close: vi.fn() })),
  openTopic: vi.fn(() => ({ close: vi.fn() })),
}))

beforeEach(() => {
  vi.mocked(openWatch).mockClear()
  sessionStorage.clear()
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
  server.use(
    http.get('/api/races/1', () => HttpResponse.json(bahrain2024)),
    http.get('/api/sessions/5/replay', () => HttpResponse.json(squareRace)),
    http.get('/api/sessions/5/replay/snapshots', () => HttpResponse.json([])),
    http.get('/api/races/1/timeline', () =>
      HttpResponse.json({ raceId: 1, sessionId: 5, clockStart: squareRace.clockStart, events: [] }),
    ),
  )
})

test("opens the race, then plays its Race session's replay", async () => {
  server.use(http.post('/api/races/1/import', () => HttpResponse.json(ready)))
  renderRoute('/races/2024/1/replay')

  expect(await screen.findByTestId('lap-counter')).toHaveTextContent('Lap 1 / 3')
  expect(screen.getByRole('heading', { name: /bahrain grand prix/i })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Bahrain Grand Prix' })).toHaveAttribute('href', '/races/2024/1')
})

test('while the race data downloads, says so and waits for it', async () => {
  let polls = 0
  server.use(
    http.post('/api/races/1/import', () => HttpResponse.json({ ...ready, state: 'IMPORTING' }, { status: 202 })),
    http.get('/api/races/1/import-status', () =>
      HttpResponse.json(++polls < 2 ? { ...ready, state: 'IMPORTING' } : ready),
    ),
  )
  renderRoute('/races/2024/1/replay')

  expect(await screen.findByText(/downloading race data/i)).toBeInTheDocument()
  expect(await screen.findByTestId('lap-counter', {}, { timeout: 5000 })).toBeInTheDocument()
})

test('a failed download shows why', async () => {
  server.use(
    http.post('/api/races/1/import', () =>
      HttpResponse.json({ ...ready, state: 'FAILED', message: 'OpenF1 timed out' }),
    ),
  )
  renderRoute('/races/2024/1/replay')

  expect(await screen.findByRole('alert')).toHaveTextContent('OpenF1 timed out')
})

test('"Watch together" starts a shared session you host, with a link to share', async () => {
  let body: unknown
  server.use(
    http.post('/api/races/1/import', () => HttpResponse.json(ready)),
    http.post('/api/watch', async ({ request }) => {
      body = await request.json()
      return HttpResponse.json({ watchId: 'w1', hostKey: 'k1', sessionId: 5 }, { status: 201 })
    }),
  )
  const { router } = renderRoute('/races/2024/1/replay')

  await userEvent.click(await screen.findByRole('button', { name: /watch together/i }))

  await vi.waitFor(() => expect(router.state.location.search).toBe('?watch=w1'))
  expect(body).toEqual({ sessionId: 5 })
  await vi.waitFor(() => expect(openWatch).toHaveBeenCalledWith('w1', 'k1', expect.any(Function)))
  expect(screen.getByRole('textbox', { name: /share/i })).toHaveValue(
    `${window.location.origin}/races/2024/1/replay?watch=w1`,
  )
  expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument() // the host has the controls
})

test('opening a shared link joins as a viewer', async () => {
  server.use(http.post('/api/races/1/import', () => HttpResponse.json(ready)))
  renderRoute('/races/2024/1/replay?watch=w1')

  expect(await screen.findByText(/following the host/i)).toBeInTheDocument()
  expect(openWatch).toHaveBeenCalledWith('w1', null, expect.any(Function))
  expect(screen.queryByRole('button', { name: /watch together/i })).not.toBeInTheDocument()
})

test("the race state's snapshots drive the overlays: the safety car's banner", async () => {
  vi.mocked(HTMLCanvasElement.prototype.getContext).mockRestore()
  const ctx = new Proxy({}, { get: (target: Record<string, unknown>, key: string) => (target[key] ??= vi.fn()) })
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as unknown as CanvasRenderingContext2D)
  server.use(
    http.post('/api/races/1/import', () => HttpResponse.json(ready)),
    http.get('/api/sessions/5/replay/snapshots', () =>
      HttpResponse.json([
        { t: 0, lap: 1, reason: 'CHANGE', flag: 'SAFETY_CAR', yellowSectors: {}, drsEnabled: false, cars: {} },
      ]),
    ),
  )
  renderRoute('/races/2024/1/replay')

  await vi.waitFor(() => expect(document.querySelector('canvas')).toHaveAttribute('data-banner', 'SAFETY CAR'))
})

test('without snapshots the replay still plays', async () => {
  server.use(
    http.post('/api/races/1/import', () => HttpResponse.json(ready)),
    http.get('/api/sessions/5/replay/snapshots', () => new HttpResponse(null, { status: 500 })),
  )
  renderRoute('/races/2024/1/replay')

  expect(await screen.findByTestId('lap-counter')).toHaveTextContent('Lap 1 / 3')
})

test('a link to the telemetry, which follows the same watch session', async () => {
  server.use(http.post('/api/races/1/import', () => HttpResponse.json(ready)))
  renderRoute('/races/2024/1/replay?watch=w1')

  expect(await screen.findByRole('link', { name: /telemetry/i })).toHaveAttribute(
    'href',
    '/races/2024/1/telemetry?watch=w1',
  )
})

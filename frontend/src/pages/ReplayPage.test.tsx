import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, expect, test, vi } from 'vitest'
import { bahrain2024 } from '../test/fixtures/api'
import { ready, squareRace } from '../test/fixtures/replay'
import { renderRoute } from '../test/render'
import { server } from '../test/server'

// /races/:year/:raceId/replay: opening the race downloads its data once, then the player loads the
// Race session's replay.
beforeEach(() => {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
  server.use(
    http.get('/api/races/1', () => HttpResponse.json(bahrain2024)),
    http.get('/api/sessions/5/replay', () => HttpResponse.json(squareRace)),
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

import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { beforeEach, expect, test } from 'vitest'
import { bahrain2024 } from '../test/fixtures/api'
import { ready, squareRace } from '../test/fixtures/replay'
import { lecLap36, verLap39, verVsLec } from '../test/fixtures/telemetry'
import { renderRoute } from '../test/render'
import { server } from '../test/server'

// /races/:year/:raceId/telemetry?driver=VER&vs=LEC — telemetry against distance, fetched on demand.
const replay = {
  ...squareRace,
  drivers: [
    ...squareRace.drivers,
    { number: 16, code: 'LEC', name: 'Charles LECLERC', team: 'Ferrari', teamColour: 'e8002d' },
  ],
}

beforeEach(() => {
  server.use(
    http.get('/api/races/1', () => HttpResponse.json(bahrain2024)),
    http.post('/api/races/1/import', () => HttpResponse.json(ready)),
    http.get('/api/sessions/5/replay', () => HttpResponse.json(replay)),
    http.get('/api/sessions/5/telemetry/lap', ({ request }) =>
      HttpResponse.json(new URL(request.url).searchParams.get('driver') === '16' ? lecLap36 : verLap39),
    ),
    http.get('/api/sessions/5/telemetry/compare', () => HttpResponse.json(verVsLec)),
  )
})

test('two drivers compared: traces against distance, the delta, sector deltas', async () => {
  const { container } = renderRoute('/races/2024/1/telemetry?driver=VER&vs=LEC')

  // six charts: on a cold run, rendering them can take longer than the default 1 s wait
  expect(await screen.findByRole('heading', { name: 'Speed' }, { timeout: 5000 })).toBeInTheDocument()
  for (const name of ['Throttle', 'Brake', 'Gear', 'RPM', 'Delta']) {
    expect(screen.getByRole('heading', { name })).toBeInTheDocument()
  }
  expect(container.querySelectorAll('.recharts-line').length).toBeGreaterThanOrEqual(11) // two per trace, plus the delta
  expect(screen.getByRole('region', { name: /VER lap 39/i })).toHaveTextContent('1:32.608')
  expect(screen.getByRole('region', { name: /LEC lap 36/i })).toHaveTextContent('1:34.090')
  const sectors = screen.getByRole('table', { name: /sectors/i })
  expect(within(sectors).getByText('+0.322')).toBeInTheDocument()
  expect(within(sectors).getByText('+0.734')).toBeInTheDocument()
  expect(screen.getByText('+1.482')).toBeInTheDocument()
})

test('tyres, and fuel and ERS shown as estimates', async () => {
  renderRoute('/races/2024/1/telemetry?driver=VER')

  const ver = await screen.findByRole('region', { name: /VER lap 39/i })
  expect(ver).toHaveTextContent('SOFT')
  expect(ver).toHaveTextContent('stint 3')
  expect(ver).toHaveTextContent('1 lap old')
  expect(ver).toHaveTextContent(/fuel.*36\.7 kg.*estimate/i)
  expect(ver).toHaveTextContent(/ERS.*4 MJ.*2 MJ/i)
  expect(screen.queryByRole('heading', { name: 'Delta' })).not.toBeInTheDocument()
})

test('a loading state while the telemetry downloads (the first time takes a few seconds)', async () => {
  server.use(
    http.get('/api/sessions/5/telemetry/lap', async () => {
      await delay(300)
      return HttpResponse.json(verLap39)
    }),
  )
  renderRoute('/races/2024/1/telemetry?driver=VER')

  expect(await screen.findByText(/downloading telemetry/i)).toBeInTheDocument()
  expect(await screen.findByRole('heading', { name: 'Speed' })).toBeInTheDocument()
})

test('choosing the drivers changes the URL', async () => {
  const { router } = renderRoute('/races/2024/1/telemetry?driver=VER')

  await userEvent.selectOptions(await screen.findByLabelText('Compare with'), 'LEC')

  expect(router.state.location.search).toBe('?driver=VER&vs=LEC')
})

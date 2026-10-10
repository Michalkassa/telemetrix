import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import ausRace from '../test/fixtures/australia1998/race.json'
import ausImport from '../test/fixtures/australia1998/import-status.json'
import ausResults from '../test/fixtures/australia1998/results.json'
import ausLaps from '../test/fixtures/australia1998/lap-times.json'
import bahRace from '../test/fixtures/bahrain2024/race.json'
import bahImport from '../test/fixtures/bahrain2024/import-status.json'
import bahResults from '../test/fixtures/bahrain2024/results.json'
import bahLaps from '../test/fixtures/bahrain2024/lap-times.json'
import bahReplay from '../test/fixtures/bahrain2024/replay.json'
import bahTimeline from '../test/fixtures/bahrain2024/timeline.json'
import { renderRoute } from '../test/render'
import { server } from '../test/server'

// The race hub and its leaves, with every response as the API served it (recorded): Bahrain 2024 (all
// leaves) and the 1998 Australian GP (before OpenF1: results and lap pace only).
vi.mock('../watch/channel', () => ({
  openWatch: vi.fn(() => ({ send: vi.fn(), close: vi.fn() })),
  openTopic: vi.fn(() => ({ close: vi.fn() })),
}))

beforeEach(() => {
  const ctx = new Proxy({}, { get: (target: Record<string, unknown>, key: string) => (target[key] ??= vi.fn()) })
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as unknown as CanvasRenderingContext2D)
  server.use(
    http.get('/api/races/1', () => HttpResponse.json(bahRace)),
    http.post('/api/races/1/import', () => HttpResponse.json(bahImport)),
    http.get('/api/races/1/results', () => HttpResponse.json(bahResults)),
    http.get('/api/races/1/lap-times', () => HttpResponse.json(bahLaps)),
    http.get('/api/races/1/timeline', () => HttpResponse.json(bahTimeline)),
    http.get('/api/sessions/5/replay', () => HttpResponse.json(bahReplay)),
    http.get('/api/sessions/5/replay/snapshots', () => HttpResponse.json([])),
    http.get('/api/races/684', () => HttpResponse.json(ausRace)),
    http.post('/api/races/684/import', () => HttpResponse.json(ausImport)),
    http.get('/api/races/684/results', () => HttpResponse.json(ausResults)),
    http.get('/api/races/684/lap-times', () => HttpResponse.json(ausLaps)),
  )
})

const leaves = () => screen.getByRole('list', { name: 'Race pages' })

describe('the hub', () => {
  test('a 2024 race links to every leaf, and shows the podium', async () => {
    renderRoute('/races/2024/1')

    await screen.findByRole('heading', { name: bahRace.name })
    const links = within(leaves()).getAllByRole('link')
    expect(links.map((a) => [a.textContent, a.getAttribute('href')])).toEqual([
      [expect.stringContaining('Results'), '/races/2024/1/results'],
      [expect.stringContaining('Timeline'), '/races/2024/1/timeline'],
      [expect.stringContaining('Lap pace'), '/races/2024/1/laps'],
      [expect.stringContaining('Telemetry'), '/races/2024/1/telemetry'],
      [expect.stringContaining('Watch replay'), '/races/2024/1/replay'],
    ])
    const podium = await screen.findByRole('list', { name: 'Podium' })
    expect(
      within(podium)
        .getAllByRole('listitem')
        .map((li) => li.textContent),
    ).toEqual(bahResults.results.slice(0, 3).map((r) => expect.stringContaining(r.surname)))
  })

  test('a 1998 race has results and lap pace; replay, timeline and telemetry are greyed out, and why', async () => {
    renderRoute('/races/1998/684')

    await screen.findByRole('heading', { name: ausRace.name })
    expect(
      within(leaves())
        .getAllByRole('link')
        .map((a) => a.getAttribute('href')),
    ).toEqual(['/races/1998/684/results', '/races/1998/684/laps'])
    for (const name of ['Timeline', 'Telemetry', 'Watch replay']) {
      const item = within(leaves()).getByText(name).closest('li')!
      expect(item).toHaveAttribute('aria-disabled', 'true')
    }
    expect(screen.getByText(/replays start in 2023/i)).toBeInTheDocument()
  })
})

describe('the leaves', () => {
  test('results: the classification as the API gives it (surnames where there is no code)', async () => {
    renderRoute('/races/1998/684/results')

    const rows = within(await screen.findByRole('table', { name: 'Results' }))
      .getAllByRole('row')
      .slice(1)
    expect(rows).toHaveLength(ausResults.results.length)
    const [winner, second] = ausResults.results
    expect(rows[0]).toHaveTextContent(winner.surname)
    expect(rows[0]).toHaveTextContent(winner.team)
    expect(rows[0]).toHaveTextContent('1:31:46.000') // 5,506,000 ms
    expect(rows[1]).toHaveTextContent(`+${((second.timeMs! - winner.timeMs!) / 1000).toFixed(3)}`)
    expect(rows.at(-1)).toHaveTextContent(ausResults.results.at(-1)!.statusDetail)
  })

  test('lap pace: a line per driver shown, the top five to begin with, and where the times come from', async () => {
    const { container } = renderRoute('/races/1998/684/laps')

    expect(await screen.findByText(/F1 history \(Jolpica\)/i)).toBeInTheDocument()
    await vi.waitFor(() => expect(container.querySelectorAll('.recharts-line')).toHaveLength(5))
    const sixth = ausLaps.drivers[5]
    await userEvent.click(screen.getByRole('checkbox', { name: sixth.code ?? sixth.surname! }))
    expect(container.querySelectorAll('.recharts-line')).toHaveLength(6)
  })

  test('lap pace for an opened race comes from OpenF1', async () => {
    renderRoute('/races/2024/1/laps')
    expect(await screen.findByText(/OpenF1/)).toBeInTheDocument()
  })

  test('timeline: clicking an event opens the replay at that moment', async () => {
    const incident = bahTimeline.events.find((e) => e.type === 'INCIDENT')!
    const { router } = renderRoute('/races/2024/1/timeline')

    const list = await screen.findByRole('list', { name: 'Race events' })
    await userEvent.click(within(list).getByRole('button', { name: new RegExp(`^${incident.summary}`) }))

    expect(router.state.location.pathname).toBe('/races/2024/1/replay')
    expect(router.state.location.search).toBe(`?t=${incident.t}`)
    await vi.waitFor(() =>
      expect(screen.getByTestId('lap-counter')).toHaveTextContent(`Lap ${incident.lap} / ${bahReplay.totalLaps}`),
    )
  })
})

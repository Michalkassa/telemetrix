import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, expect, test, vi } from 'vitest'
import race from '../test/fixtures/singapore2023/race.json'
import importStatus from '../test/fixtures/singapore2023/import-status.json'
import replay from '../test/fixtures/singapore2023/replay.json'
import snapshots from '../test/fixtures/singapore2023/snapshots.json'
import timeline from '../test/fixtures/singapore2023/timeline.json'
import { openTopic } from '../watch/channel'
import { renderRoute } from '../test/render'
import { server } from '../test/server'

// The replay page with its timeline: Singapore 2023, every response as the API served it.
vi.mock('../watch/channel', () => ({
  openWatch: vi.fn(() => ({ send: vi.fn(), close: vi.fn() })),
  openTopic: vi.fn(() => ({ close: vi.fn() })),
}))

beforeEach(() => {
  const ctx = new Proxy({}, { get: (target: Record<string, unknown>, key: string) => (target[key] ??= vi.fn()) })
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as unknown as CanvasRenderingContext2D)
  server.use(
    http.get('/api/races/39', () => HttpResponse.json(race)),
    http.post('/api/races/39/import', () => HttpResponse.json(importStatus)),
    http.get('/api/races/39/timeline', () => HttpResponse.json(timeline)),
    http.get('/api/sessions/15/replay', () => HttpResponse.json(replay)),
    http.get('/api/sessions/15/replay/snapshots', () => HttpResponse.json(snapshots)),
  )
})

test('clicking the safety car in the event list moves the player there', async () => {
  const sc = timeline.events.find((e) => e.type === 'SAFETY_CAR')!
  renderRoute('/races/2023/39/replay')

  const list = await screen.findByRole('list', { name: 'Race events' })
  await userEvent.click(within(list).getByRole('button', { name: new RegExp(`^${sc.summary}`) }))

  await vi.waitFor(() =>
    expect(screen.getByTestId('lap-counter')).toHaveTextContent(`Lap ${sc.lap} / ${replay.totalLaps}`),
  )
  expect(screen.getByRole('slider', { name: 'Race time' })).toHaveValue(String(Math.floor(sc.t / 1000) * 1000))
  expect(screen.getByRole('button', { name: `Marker: ${sc.summary}, lap ${sc.lap}` })).toBeInTheDocument()
  // the event just clicked is the current one (not the one before it, at the same whole second)
  await vi.waitFor(() => expect(list.querySelector('[aria-current="true"]')).toHaveTextContent(sc.summary))
})

test('a pushed timeline update (the race imported again) fetches the timeline again', async () => {
  let fetched = 0
  server.use(
    http.get('/api/races/39/timeline', () => {
      fetched++
      return HttpResponse.json(timeline)
    }),
  )
  renderRoute('/races/2023/39/replay')
  await screen.findByRole('list', { name: 'Race events' })
  expect(fetched).toBe(1)

  await vi.waitFor(() => expect(openTopic).toHaveBeenCalledWith('/topic/races/39/timeline', expect.any(Function)))
  const push = vi.mocked(openTopic).mock.calls.at(-1)![1]
  push({ raceId: 39, change: 'TIMELINE' })

  await vi.waitFor(() => expect(fetched).toBe(2))
})

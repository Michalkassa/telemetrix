import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, test } from 'vitest'
import { bahrain2024, britishGp1950, races2023, races2024, seasons } from '../test/fixtures/api'
import { renderRoute } from '../test/render'
import { server } from '../test/server'

// The drill-down from plan.md: landing -> dashboard -> Grand Prix -> year -> race.
beforeEach(() => {
  server.use(
    http.get('/api/seasons', () => HttpResponse.json(seasons)),
    http.get('/api/seasons/2024/races', () => HttpResponse.json(races2024)),
    http.get('/api/seasons/2023/races', () => HttpResponse.json(races2023)),
    http.get('/api/races/1', () => HttpResponse.json(bahrain2024)),
    http.get('/api/races/900', () => HttpResponse.json(britishGp1950)),
  )
})

describe('landing page', () => {
  test('invites you into the dashboard', async () => {
    renderRoute('/')

    expect(screen.getByRole('heading', { level: 1, name: /telemetrix/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /enter dashboard/i })).toHaveAttribute('href', '/dashboard')
  })
})

describe('dashboard', () => {
  test('offers four lenses, with Grand Prix open for now', async () => {
    renderRoute('/dashboard')

    const cards = screen.getAllByRole('article')
    expect(cards.map((card) => within(card).getByRole('heading').textContent)).toEqual([
      'Seasons',
      'Grand Prix',
      'Drivers & Teams',
      'Replay Theater',
    ])
    expect(screen.getByRole('link', { name: /grand prix/i })).toHaveAttribute('href', '/races')
    expect(screen.getAllByText(/coming soon/i)).toHaveLength(3)
  })
})

describe('Grand Prix list', () => {
  test('/races opens the newest season that has races', async () => {
    const { router } = renderRoute('/races')

    await waitFor(() => expect(router.state.location.pathname).toBe('/races/2024'))
  })

  test("lists a season's races in round order, each linking to its race", async () => {
    renderRoute('/races/2024')

    const items = within(await screen.findByRole('list', { name: 'Races' })).getAllByRole('listitem')
    expect(items.map((li) => within(li).getByRole('link').textContent)).toEqual([
      expect.stringContaining('Bahrain Grand Prix'),
      expect.stringContaining('Saudi Arabian Grand Prix'),
      expect.stringContaining('Australian Grand Prix'),
    ])
    expect(within(items[0]).getByRole('link')).toHaveAttribute('href', '/races/2024/1')
    expect(items[0]).toHaveTextContent('Sakhir')
    expect(items[0]).toHaveTextContent('2 Mar 2024')
  })

  test('choosing another year changes the URL and the list', async () => {
    const { router } = renderRoute('/races/2024')
    await screen.findByText('Australian Grand Prix')

    await userEvent.selectOptions(await screen.findByLabelText(/season/i), '2023')

    expect(router.state.location.pathname).toBe('/races/2023')
    await waitFor(() => expect(screen.queryByText('Australian Grand Prix')).not.toBeInTheDocument())
    expect(within(screen.getByRole('list', { name: 'Races' })).getAllByRole('listitem')).toHaveLength(2)
  })

  test('shows that it is loading', async () => {
    server.use(
      http.get('/api/seasons/2024/races', async () => {
        await delay('infinite')
        return HttpResponse.json(races2024)
      }),
    )

    renderRoute('/races/2024')

    expect(await screen.findByText(/loading races/i)).toBeInTheDocument()
  })

  test('says what went wrong', async () => {
    server.use(
      http.get('/api/seasons/1999/races', () =>
        HttpResponse.json({ status: 404, title: 'Not Found', detail: 'Season 1999 not found' }, { status: 404 }),
      ),
    )

    renderRoute('/races/1999')

    expect(await screen.findByRole('alert')).toHaveTextContent('Season 1999 not found')
  })
})

describe('breadcrumbs', () => {
  test('lead back up from a year', async () => {
    renderRoute('/races/2024')

    const crumbs = within(await screen.findByRole('navigation', { name: /breadcrumb/i }))
    expect(crumbs.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('href', '/dashboard')
    expect(crumbs.getByRole('link', { name: 'Grand Prix' })).toHaveAttribute('href', '/races')
    expect(crumbs.getByText('2024')).toHaveAttribute('aria-current', 'page')
  })

  test('lead back up from a race', async () => {
    renderRoute('/races/2024/1')

    const crumbs = within(await screen.findByRole('navigation', { name: /breadcrumb/i }))
    expect(crumbs.getByRole('link', { name: '2024' })).toHaveAttribute('href', '/races/2024')
    expect(await crumbs.findByText('Bahrain Grand Prix')).toHaveAttribute('aria-current', 'page')
  })
})

describe('race page', () => {
  test('shows the race, its sessions and the way to its replay', async () => {
    renderRoute('/races/2024/1')

    expect(await screen.findByRole('heading', { level: 1, name: 'Bahrain Grand Prix' })).toBeInTheDocument()
    expect(screen.getByText(/Bahrain International Circuit/)).toBeInTheDocument()
    expect(screen.getByText('Race')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /watch replay/i })).toHaveAttribute('href', '/races/2024/1/replay')
  })

  test('explains why a race before 2023 has no replay', async () => {
    renderRoute('/races/1950/900')

    expect(await screen.findByRole('heading', { level: 1, name: 'British Grand Prix' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /watch replay/i })).not.toBeInTheDocument()
    expect(screen.getByText(/replays start in 2023/i)).toBeInTheDocument()
  })
})

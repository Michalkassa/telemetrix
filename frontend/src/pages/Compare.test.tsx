import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, test } from 'vitest'
import race from '../test/fixtures/compare/race-monza-2024.json'
import seasons from '../test/fixtures/compare/monza-seasons.json'
import monza from '../test/fixtures/compare/monza-2023-2024.json'
import standings from '../test/fixtures/compare/standings-2024.json'
import verLec from '../test/fixtures/compare/ver-lec-2024.json'
import verPer from '../test/fixtures/compare/ver-per-2024.json'
import verstappen from '../test/fixtures/drivers/max_verstappen-stats.json'
import { renderRoute } from '../test/render'
import { server } from '../test/server'

// The comparisons, with every response as the API served it (recorded): Monza 2024 against 2023 (both
// races opened, so with compounds), and Verstappen against Leclerc and against Pérez in 2024.
const requested: string[] = []
beforeEach(() => {
  requested.length = 0
  server.use(
    http.get('/api/races/16', () => HttpResponse.json(race)),
    http.get('/api/compare/circuits/monza/seasons', () => HttpResponse.json(seasons)),
    http.get('/api/compare/circuits/monza', ({ request }) => {
      requested.push(new URL(request.url).search)
      return HttpResponse.json(monza)
    }),
    http.get('/api/seasons/2024/standings', () => HttpResponse.json(standings)),
    http.get('/api/drivers/max_verstappen/stats', () => HttpResponse.json(verstappen)),
    http.get('/api/compare/drivers', ({ request }) => {
      requested.push(new URL(request.url).search)
      return HttpResponse.json(new URL(request.url).searchParams.get('b') === 'perez' ? verPer : verLec)
    }),
  )
})

const rowsOf = (table: HTMLElement) => within(table).getAllByRole('row').slice(1)

describe('the same circuit across years', () => {
  test('without ?vs, a race is compared with the previous Grand Prix there', async () => {
    renderRoute('/races/2024/16/compare')

    expect(await screen.findByRole('combobox', { name: 'Compare with' })).toHaveValue('2023')
    expect(requested).toContain('?years=2024,2023')
  })

  test('the two races: winners and fastest laps, and the pace lap by lap', async () => {
    renderRoute('/races/2024/16/compare?vs=2023')

    const rows = rowsOf(await screen.findByRole('table', { name: 'The races' }))
    expect(rows).toHaveLength(monza.years.length)
    for (const [i, year] of monza.years.entries()) {
      expect(rows[i]).toHaveTextContent(String(year.season))
      expect(rows[i]).toHaveTextContent(year.winner.surname)
      expect(rows[i]).toHaveTextContent(year.fastestLap.code)
    }
    expect(screen.getByTestId('pace-chart')).toBeInTheDocument()
  })

  test('both strategies side by side, every stint with its compound and laps', async () => {
    renderRoute('/races/2024/16/compare?vs=2023')

    for (const year of monza.years) {
      const list = await screen.findByRole('list', { name: `Strategies ${year.season}` })
      const cars = within(list).getAllByRole('listitem')
      expect(cars).toHaveLength(year.strategies.length)
      const winner = year.strategies[0]
      expect(cars[0]).toHaveTextContent(winner.code)
      for (const stint of winner.stints) {
        expect(within(cars[0]).getByTitle(`${stint.compound} laps ${stint.fromLap}–${stint.toLap}`)).toBeInTheDocument()
      }
    }
  })

  test('picking another year asks for that comparison', async () => {
    const { router } = renderRoute('/races/2024/16/compare?vs=2023')

    const picker = await screen.findByRole('combobox', { name: 'Compare with' })
    expect(within(picker).getAllByRole('option')).toHaveLength(seasons.length - 1) // not this race's year
    await userEvent.selectOptions(picker, '2019')
    expect(router.state.location.search).toBe('?vs=2019')
  })

  test('the race hub links to it', async () => {
    server.use(http.get('/api/races/16/results', () => HttpResponse.json({ raceId: 16, results: [] })))
    renderRoute('/races/2024/16')

    const leaves = within(await screen.findByRole('list', { name: 'Race pages' }))
    expect(leaves.getByRole('link', { name: /Compare years/ })).toHaveAttribute('href', '/races/2024/16/compare')
  })
})

describe('driver against driver', () => {
  test('the head-to-head tally and every race they both entered', async () => {
    renderRoute('/drivers/max_verstappen/compare?season=2024&vs=leclerc')

    const tally = await screen.findByRole('list', { name: 'Head to head' })
    expect(within(tally).getByText('Ahead at the finish').nextSibling).toHaveTextContent(
      `${verLec.tally.aAheadInRace}–${verLec.tally.bAheadInRace}`,
    )
    expect(within(tally).getByText('Ahead on the grid').nextSibling).toHaveTextContent(
      `${verLec.tally.aAheadOnGrid}–${verLec.tally.bAheadOnGrid}`,
    )
    const rows = rowsOf(screen.getByRole('table', { name: 'Race by race' }))
    expect(rows).toHaveLength(verLec.races.length)
    const first = verLec.races[0]
    expect(within(rows[0]).getByRole('link', { name: first.name })).toHaveAttribute('href', `/races/2024/${first.raceId}`)
    expect(rows[0]).toHaveTextContent(`${(first.medianGapMs! / 1000).toFixed(3)} s`)
    expect(screen.getByTestId('gap-chart')).toBeInTheDocument()
  })

  test('without ?vs the opponent is the team-mate', async () => {
    renderRoute('/drivers/max_verstappen/compare?season=2024')

    expect(await screen.findByRole('combobox', { name: 'Against' })).toHaveValue('perez')
    expect(requested).toContain('?a=max_verstappen&b=perez&season=2024')
  })

  test('the driver hub has the tab', async () => {
    renderRoute('/drivers/max_verstappen')

    const tabs = within(await screen.findByRole('navigation', { name: 'Driver pages' }))
    expect(tabs.getByRole('link', { name: 'Head to head' })).toHaveAttribute('href', '/drivers/max_verstappen/compare')
  })
})

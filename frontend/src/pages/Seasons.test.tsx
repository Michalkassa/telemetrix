import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import seasons from '../test/fixtures/season2021/seasons.json'
import standings from '../test/fixtures/season2021/standings.json'
import grid from '../test/fixtures/season2021/results-grid.json'
import progression from '../test/fixtures/season2021/progression.json'
import progression2020 from '../test/fixtures/season2021/progression-2020.json'
import races from '../test/fixtures/season2021/races.json'
import { renderRoute } from '../test/render'
import { server } from '../test/server'

// The Seasons lens, with every response as the API served it (recorded): the 2021 season, and 2020's
// points progression for a comparison.
beforeEach(() => {
  server.use(
    http.get('/api/seasons', () => HttpResponse.json(seasons)),
    http.get('/api/seasons/2021/standings', () => HttpResponse.json(standings)),
    http.get('/api/seasons/2021/results-grid', () => HttpResponse.json(grid)),
    http.get('/api/seasons/2021/progression', () => HttpResponse.json(progression)),
    http.get('/api/seasons/2020/progression', () => HttpResponse.json(progression2020)),
    http.get('/api/seasons/2021/races', () => HttpResponse.json(races)),
  )
})

test('/seasons: every season, newest first, each leading to its hub', async () => {
  renderRoute('/seasons')

  const links = within(await screen.findByRole('list', { name: 'Seasons' })).getAllByRole('link')
  expect(links).toHaveLength(seasons.length)
  expect(links[0]).toHaveTextContent(String(seasons[0].year))
  expect(links.find((a) => a.textContent?.includes('2021'))).toHaveAttribute('href', '/seasons/2021')
})

test('the season hub: the champions and the way to every page', async () => {
  renderRoute('/seasons/2021')

  expect(await screen.findByText(new RegExp(standings.drivers[0].surname))).toBeInTheDocument()
  expect(screen.getByText(new RegExp(standings.constructors[0].name))).toBeInTheDocument()
  expect(within(screen.getByRole('list', { name: 'Season pages' })).getAllByRole('link').map((a) => a.getAttribute('href'))).toEqual([
    '/seasons/2021/standings/drivers',
    '/seasons/2021/standings/constructors',
    '/seasons/2021/results',
    '/seasons/2021/races',
    '/seasons/2021/compare',
  ])
})

describe('standings', () => {
  test("the drivers' standings, and sorting by a column", async () => {
    renderRoute('/seasons/2021/standings/drivers')

    const table = await screen.findByRole('table', { name: "Drivers' standings" })
    const rows = () => within(table).getAllByRole('row').slice(1)
    expect(rows()).toHaveLength(standings.drivers.length)
    expect(rows()[0]).toHaveTextContent(standings.drivers[0].surname)
    expect(rows()[0]).toHaveTextContent(String(standings.drivers[0].points))

    await userEvent.click(within(table).getByRole('button', { name: /driver/i }))
    const bySurname = [...standings.drivers].sort((a, b) => a.surname.localeCompare(b.surname))
    expect(rows()[0]).toHaveTextContent(bySurname[0].surname)

    await userEvent.click(within(table).getByRole('button', { name: /wins/i }))
    const mostWins = Math.max(...standings.drivers.map((d) => d.wins))
    expect(within(rows()[0]).getByTestId('wins')).toHaveTextContent(String(mostWins))
  })

  test("the constructors' standings", async () => {
    renderRoute('/seasons/2021/standings/constructors')

    const rows = within(await screen.findByRole('table', { name: "Constructors' standings" })).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(standings.constructors.length)
    expect(rows[0]).toHaveTextContent(standings.constructors[0].name)
  })
})

test('the results grid: every cell links to its race hub (the Abu Dhabi finale included)', async () => {
  renderRoute('/seasons/2021/results')

  const table = await screen.findByRole('table', { name: 'Results grid' })
  const finale = grid.races.at(-1)!
  expect(within(table).getByRole('link', { name: new RegExp(finale.name) })).toHaveAttribute('href', `/races/2021/${finale.raceId}`)
  for (const driver of grid.drivers) {
    const row = within(table).getByRole('row', { name: new RegExp(driver.surname) })
    const links = within(row).getAllByRole('link')
    expect(links.map((a) => a.getAttribute('href'))).toEqual(driver.cells.map((c) => `/races/2021/${c.raceId}`))
  }
  const champion = grid.drivers[0]
  const finaleCell = champion.cells.find((c) => c.raceId === finale.raceId)!
  expect(within(within(table).getByRole('row', { name: new RegExp(champion.surname) })).getAllByRole('link').at(-1)).toHaveTextContent(String(finaleCell.position))
})

test("the season's races, each linking to its hub", async () => {
  renderRoute('/seasons/2021/races')

  const links = within(await screen.findByRole('list', { name: 'Races' })).getAllByRole('link')
  expect(links).toHaveLength(races.races.length)
  expect(links[0]).toHaveAttribute('href', `/races/2021/${races.races[0].id}`)
})

test("comparison: two drivers' points through two seasons, as two series", async () => {
  const a = progression.drivers[0]
  const b = progression2020.drivers[0]
  const { container } = renderRoute(`/seasons/2021/compare?a=2021:${a.ref}&b=2020:${b.ref}`)

  await screen.findByRole('heading', { name: /points progression/i })
  await vi.waitFor(() => expect(container.querySelectorAll('.recharts-line')).toHaveLength(2))
  expect(screen.getByText(`${a.code} 2021 · ${a.rounds.at(-1)!.points}`)).toBeInTheDocument()
  expect(screen.getByText(`${b.code} 2020 · ${b.rounds.at(-1)!.points}`)).toBeInTheDocument()
})

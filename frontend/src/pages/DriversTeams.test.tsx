import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, test } from 'vitest'
import drivers from '../test/fixtures/drivers/drivers.json'
import verstappen from '../test/fixtures/drivers/max_verstappen-stats.json'
import verstappenRaces from '../test/fixtures/drivers/max_verstappen-races.json'
import constructors from '../test/fixtures/drivers/constructors.json'
import mclaren from '../test/fixtures/drivers/mclaren-2024.json'
import mclarenLatest from '../test/fixtures/drivers/mclaren-latest.json'
import standings from '../test/fixtures/season2021/standings.json'
import grid from '../test/fixtures/season2021/results-grid.json'
import { renderRoute } from '../test/render'
import { server } from '../test/server'

// The Drivers & Teams lens, with every response as the API served it (recorded): the directories, Max
// Verstappen's career and race log, McLaren's 2024 and latest seasons.
beforeEach(() => {
  server.use(
    http.get('/api/drivers', () => HttpResponse.json(drivers)),
    http.get('/api/drivers/max_verstappen/stats', () => HttpResponse.json(verstappen)),
    http.get('/api/drivers/max_verstappen/races', () => HttpResponse.json(verstappenRaces)),
    http.get('/api/constructors', () => HttpResponse.json(constructors)),
    http.get('/api/constructors/mclaren/stats', ({ request }) =>
      HttpResponse.json(new URL(request.url).searchParams.get('season') === '2024' ? mclaren : mclarenLatest),
    ),
    http.get('/api/seasons/2021/standings', () => HttpResponse.json(standings)),
    http.get('/api/seasons/2021/results-grid', () => HttpResponse.json(grid)),
  )
})

const rowsOf = (table: HTMLElement) => within(table).getAllByRole('row').slice(1)

describe('the drivers', () => {
  test('/drivers: everyone who has raced, latest first, each leading to their hub', async () => {
    renderRoute('/drivers')

    const rows = rowsOf(await screen.findByRole('table', { name: 'Drivers' }))
    expect(rows).toHaveLength(drivers.length)
    const first = drivers[0]
    expect(within(rows[0]).getByRole('link', { name: `${first.givenName} ${first.surname}` })).toHaveAttribute(
      'href',
      `/drivers/${first.ref}`,
    )
  })

  test('searching narrows the directory, and the search is in the URL', async () => {
    const { router } = renderRoute('/drivers')

    await userEvent.type(await screen.findByRole('searchbox', { name: 'Search drivers' }), 'schumacher')

    const expected = drivers.filter((d) => `${d.givenName} ${d.surname}`.toLowerCase().includes('schumacher'))
    expect(expected.length).toBeGreaterThan(1)
    expect(rowsOf(screen.getByRole('table', { name: 'Drivers' }))).toHaveLength(expected.length)
    expect(router.state.location.search).toBe('?q=schumacher')
  })

  test('a driver hub: the career, its tabs, and the way back', async () => {
    renderRoute('/drivers/max_verstappen')

    expect(await screen.findByRole('heading', { name: 'Max Verstappen' })).toBeInTheDocument()
    const career = screen.getByRole('list', { name: 'Career' })
    expect(within(career).getByText('Wins').nextSibling).toHaveTextContent(String(verstappen.career.wins))
    expect(within(career).getByText('Poles').nextSibling).toHaveTextContent(String(verstappen.career.poles))
    expect(
      within(screen.getByRole('navigation', { name: 'Driver pages' }))
        .getAllByRole('link')
        .map((a) => a.getAttribute('href')),
    ).toEqual(['/drivers/max_verstappen', '/drivers/max_verstappen/seasons', '/drivers/max_verstappen/races'])
    const crumbs = within(screen.getByRole('navigation', { name: 'Breadcrumb' }))
    expect(crumbs.getByRole('link', { name: 'Drivers' })).toHaveAttribute('href', '/drivers')
    // the most-raced track first
    expect(rowsOf(screen.getByRole('table', { name: 'Tracks' }))[0]).toHaveTextContent(verstappen.tracks[0].name)
  })

  test('season by season: each season links to that season', async () => {
    renderRoute('/drivers/max_verstappen/seasons')

    const rows = rowsOf(await screen.findByRole('table', { name: 'Seasons' }))
    expect(rows).toHaveLength(verstappen.seasons.length)
    const last = verstappen.seasons[verstappen.seasons.length - 1]
    const row = rows.find((r) => within(r).queryByRole('link', { name: String(last.season) }))!
    expect(within(row).getByRole('link', { name: String(last.season) })).toHaveAttribute(
      'href',
      `/seasons/${last.season}`,
    )
    expect(row).toHaveTextContent(String(last.careerWins))
  })

  test('the race log: each race links to its hub, each team to its page', async () => {
    renderRoute('/drivers/max_verstappen/races')

    const rows = rowsOf(await screen.findByRole('table', { name: 'Race log' }))
    expect(rows).toHaveLength(verstappenRaces.length)
    const first = verstappenRaces[0]
    expect(within(rows[0]).getByRole('link', { name: first.race })).toHaveAttribute(
      'href',
      `/races/${first.season}/${first.raceId}`,
    )
    expect(within(rows[0]).getByRole('link', { name: first.team })).toHaveAttribute(
      'href',
      `/teams/${first.teamRef}?season=${first.season}`,
    )
    // a retirement says why
    const retired = verstappenRaces.findIndex((r) => r.status.startsWith('RETIRED'))
    expect(rows[retired]).toHaveTextContent(verstappenRaces[retired].statusDetail)
  })
})

describe('the teams', () => {
  test('/teams: every constructor, each leading to its hub', async () => {
    renderRoute('/teams')

    const rows = rowsOf(await screen.findByRole('table', { name: 'Teams' }))
    expect(rows).toHaveLength(constructors.length)
    expect(within(rows[0]).getByRole('link', { name: constructors[0].name })).toHaveAttribute(
      'href',
      `/teams/${constructors[0].ref}`,
    )
  })

  test("a team hub: the season's headline numbers, a season picker, its tabs", async () => {
    const { router } = renderRoute('/teams/mclaren?season=2024')

    expect(await screen.findByRole('heading', { name: 'McLaren' })).toBeInTheDocument()
    const overview = screen.getByRole('list', { name: 'Season overview' })
    expect(within(overview).getByText('Pit stops').nextSibling).toHaveTextContent(
      `${mclaren.pitStops.rank} of ${mclaren.pitStops.teams}`,
    )
    expect(
      within(screen.getByRole('navigation', { name: 'Team pages' }))
        .getAllByRole('link')
        .map((a) => a.getAttribute('href')),
    ).toEqual([
      '/teams/mclaren?season=2024',
      '/teams/mclaren/reliability?season=2024',
      '/teams/mclaren/pace?season=2024',
    ])

    const team = constructors.find((c) => c.ref === 'mclaren')!
    const picker = screen.getByRole('combobox', { name: 'Season' })
    expect(within(picker).getAllByRole('option')).toHaveLength(team.seasons.length)
    await userEvent.selectOptions(picker, String(mclarenLatest.season))
    expect(router.state.location.search).toBe(`?season=${mclarenLatest.season}`)
  })

  test('without a season, the team hub shows its latest', async () => {
    renderRoute('/teams/mclaren')

    expect(await screen.findByRole('combobox', { name: 'Season' })).toHaveValue(String(mclarenLatest.season))
  })

  test('reliability: retirements by cause', async () => {
    renderRoute('/teams/mclaren/reliability?season=2024')

    const figures = await screen.findByRole('list', { name: 'Reliability' })
    expect(within(figures).getByText('Finishes').nextSibling).toHaveTextContent(
      `${mclaren.reliability.finishes} of ${mclaren.reliability.starts}`,
    )
    expect(screen.getByText(/no retirements/i)).toBeInTheDocument()
  })

  test("pace: the season's gaps and the development trend round by round", async () => {
    renderRoute('/teams/mclaren/pace?season=2024')

    const rows = rowsOf(await screen.findByRole('table', { name: 'Development trend' }))
    expect(rows).toHaveLength(mclaren.trend.rounds.length)
    expect(rows[0]).toHaveTextContent(mclaren.trend.rounds[0].race)
    expect(rows[0]).toHaveTextContent(`${mclaren.trend.rounds[0].qualifyingGapPercent!.toFixed(2)}%`)
    expect(screen.getByTestId('trend-chart')).toBeInTheDocument()
  })
})

describe('cross-links', () => {
  test("standings rows lead to the driver's and the team's pages", async () => {
    renderRoute('/seasons/2021/standings/drivers')
    const champion = standings.drivers[0]
    const table = await screen.findByRole('table', { name: "Drivers' standings" })
    expect(within(rowsOf(table)[0]).getByRole('link', { name: new RegExp(champion.surname) })).toHaveAttribute(
      'href',
      `/drivers/${champion.ref}`,
    )
  })

  test("the constructors' standings lead to the team's season", async () => {
    renderRoute('/seasons/2021/standings/constructors')
    const first = standings.constructors[0]
    const table = await screen.findByRole('table', { name: "Constructors' standings" })
    expect(within(rowsOf(table)[0]).getByRole('link', { name: first.name })).toHaveAttribute(
      'href',
      `/teams/${first.ref}?season=2021`,
    )
  })

  test("the results grid's drivers lead to their pages", async () => {
    renderRoute('/seasons/2021/results')
    const first = grid.drivers[0]
    const link = await screen.findByRole('link', { name: first.surname })
    expect(link).toHaveAttribute('href', `/drivers/${first.ref}`)
  })
})

import { expect, test } from '@playwright/test'
import standings from '../src/test/fixtures/season2021/standings.json' with { type: 'json' }
import drivers from '../src/test/fixtures/drivers/drivers.json' with { type: 'json' }
import constructors from '../src/test/fixtures/drivers/constructors.json' with { type: 'json' }
import verstappen from '../src/test/fixtures/drivers/max_verstappen-stats.json' with { type: 'json' }
import verstappenRaces from '../src/test/fixtures/drivers/max_verstappen-races.json' with { type: 'json' }
import redBull from '../src/test/fixtures/drivers/red_bull-2026.json' with { type: 'json' }

// From a table row to the driver's and the team's pages, and back up with the breadcrumbs; each response
// as the API served it (recorded).
test.beforeEach(async ({ page }) => {
  await page.route('**/api/seasons/2021/standings', (route) => route.fulfill({ json: standings }))
  await page.route('**/api/drivers', (route) => route.fulfill({ json: drivers }))
  await page.route('**/api/drivers/max_verstappen/stats', (route) => route.fulfill({ json: verstappen }))
  await page.route('**/api/drivers/max_verstappen/races', (route) => route.fulfill({ json: verstappenRaces }))
  await page.route('**/api/constructors', (route) => route.fulfill({ json: constructors }))
  await page.route('**/api/constructors/red_bull/stats?season=2026', (route) => route.fulfill({ json: redBull }))
})

test('standings → driver → race log → team, and back up with the breadcrumbs', async ({ page }) => {
  await page.goto('/seasons/2021/standings/drivers')
  await page
    .getByRole('table', { name: "Drivers' standings" })
    .getByRole('link', { name: /Verstappen/ })
    .click()
  await expect(page).toHaveURL(/\/drivers\/max_verstappen$/)
  await expect(page.getByRole('heading', { name: 'Max Verstappen' })).toBeVisible()

  await page.getByRole('navigation', { name: 'Driver pages' }).getByRole('link', { name: 'Race log' }).click()
  const latest = verstappenRaces[0]
  await page
    .getByRole('table', { name: 'Race log' })
    .getByRole('row')
    .nth(1)
    .getByRole('link', { name: latest.team })
    .click()
  await expect(page).toHaveURL(new RegExp(`/teams/${latest.teamRef}\\?season=${latest.season}$`))
  await expect(page.getByRole('heading', { name: redBull.constructor.name })).toBeVisible()

  const crumbs = page.getByRole('navigation', { name: 'Breadcrumb' })
  await crumbs.getByRole('link', { name: 'Teams' }).click()
  await expect(page).toHaveURL(/\/teams$/)
  await expect(page.getByRole('table', { name: 'Teams' }).getByRole('row')).toHaveCount(constructors.length + 1)
  await crumbs.getByRole('link', { name: 'Dashboard' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
})

test('the drivers directory, searched, to a hub and back', async ({ page }) => {
  await page.goto('/drivers')
  await page.getByRole('searchbox', { name: 'Search drivers' }).fill('verstappen')
  await expect(page).toHaveURL(/\?q=verstappen$/)
  await page.getByRole('table', { name: 'Drivers' }).getByRole('link', { name: 'Max Verstappen' }).click()
  await expect(page.getByRole('heading', { name: 'Max Verstappen' })).toBeVisible()

  await page.getByRole('navigation', { name: 'Breadcrumb' }).getByRole('link', { name: 'Drivers' }).click()
  await expect(page).toHaveURL(/\/drivers$/)
})

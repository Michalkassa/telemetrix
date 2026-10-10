import { expect, test } from '@playwright/test'
import race from '../src/test/fixtures/bahrain2024/race.json' with { type: 'json' }
import importStatus from '../src/test/fixtures/bahrain2024/import-status.json' with { type: 'json' }
import results from '../src/test/fixtures/bahrain2024/results.json' with { type: 'json' }
import lapTimes from '../src/test/fixtures/bahrain2024/lap-times.json' with { type: 'json' }
import replay from '../src/test/fixtures/bahrain2024/replay.json' with { type: 'json' }
import snapshots from '../src/test/fixtures/bahrain2024/snapshots.json' with { type: 'json' }
import timeline from '../src/test/fixtures/bahrain2024/timeline.json' with { type: 'json' }
import telemetryLap from '../src/test/fixtures/bahrain2024/telemetry-lap-ver.json' with { type: 'json' }

// Every leaf of one race, from its hub: Bahrain 2024, each response as the API served it (recorded).
test.beforeEach(async ({ page }) => {
  await page.route('**/api/races/1', (route) => route.fulfill({ json: race }))
  await page.route('**/api/races/1/import', (route) => route.fulfill({ json: importStatus }))
  await page.route('**/api/races/1/results', (route) => route.fulfill({ json: results }))
  await page.route('**/api/races/1/lap-times', (route) => route.fulfill({ json: lapTimes }))
  await page.route('**/api/races/1/timeline', (route) => route.fulfill({ json: timeline }))
  await page.route('**/api/sessions/5/replay/snapshots', (route) => route.fulfill({ json: snapshots }))
  await page.route('**/api/sessions/5/replay', (route) => route.fulfill({ json: replay }))
  await page.route('**/api/sessions/5/telemetry/lap?driver=1', (route) => route.fulfill({ json: telemetryLap }))
})

test('from the hub through every leaf of a 2024 race', async ({ page }) => {
  const hub = '/races/2024/1'
  const winner = results.results[0]
  await page.goto(hub)
  await expect(page.getByRole('list', { name: 'Podium' })).toContainText(winner.surname)
  const leaf = (name: string) => page.getByRole('list', { name: 'Race pages' }).getByRole('link', { name: new RegExp(name) })

  // results
  await leaf('Results').click()
  await expect(page.getByRole('table', { name: 'Results' }).getByRole('row')).toHaveCount(results.results.length + 1)
  await page.goto(hub)

  // timeline → an event opens the replay there
  await leaf('Timeline').click()
  const incident = timeline.events.find((e) => e.type === 'INCIDENT')!
  await page.getByRole('list', { name: 'Race events' }).getByRole('button', { name: new RegExp(`^${incident.summary}`) }).click()
  await expect(page).toHaveURL(new RegExp(`/replay\\?t=${incident.t}$`))
  await expect(page.getByTestId('lap-counter')).toHaveText(`Lap ${incident.lap} / ${replay.totalLaps}`)
  await page.goto(hub)

  // lap pace
  await leaf('Lap pace').click()
  await expect(page.getByText('Lap times from OpenF1')).toBeVisible()
  await expect(page.locator('.recharts-line')).toHaveCount(5)
  await page.goto(hub)

  // telemetry (the winner's fastest lap by default)
  await leaf('Telemetry').click()
  await expect(page.getByRole('region', { name: new RegExp(`lap ${telemetryLap.lap}`) })).toBeVisible()
  await page.goto(hub)

  // replay
  await leaf('Watch replay').click()
  await expect(page.getByTestId('lap-counter')).toHaveText(`Lap 1 / ${replay.totalLaps}`)
})

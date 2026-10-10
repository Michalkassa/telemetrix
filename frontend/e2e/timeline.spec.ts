import { expect, test } from '@playwright/test'
import race from '../src/test/fixtures/singapore2023/race.json' with { type: 'json' }
import importStatus from '../src/test/fixtures/singapore2023/import-status.json' with { type: 'json' }
import replay from '../src/test/fixtures/singapore2023/replay.json' with { type: 'json' }
import snapshots from '../src/test/fixtures/singapore2023/snapshots.json' with { type: 'json' }
import timeline from '../src/test/fixtures/singapore2023/timeline.json' with { type: 'json' }

// The timeline drives the player: Singapore 2023 as the API serves it (recorded responses), so every
// expected value below comes from those responses.
test.beforeEach(async ({ page }) => {
  await page.route('**/api/races/39', (route) => route.fulfill({ json: race }))
  await page.route('**/api/races/39/import', (route) => route.fulfill({ json: importStatus }))
  await page.route('**/api/races/39/timeline', (route) => route.fulfill({ json: timeline }))
  await page.route('**/api/sessions/15/replay/snapshots', (route) => route.fulfill({ json: snapshots }))
  await page.route('**/api/sessions/15/replay', (route) => route.fulfill({ json: replay }))
})

function clock(ms: number): string {
  const s = Math.floor(ms / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const ss = String(s % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`
}

test('click "Safety car" and the player seeks there, with every view in sync', async ({ page }) => {
  const sc = timeline.events.find((e) => e.type === 'SAFETY_CAR')!
  const flagThen = [...snapshots].reverse().find((s) => s.t <= sc.t)!.flag
  expect(flagThen).toBe('SAFETY_CAR') // the API's own state agrees there's a safety car then

  await page.goto('/races/2023/39/replay')
  await expect(page.getByTestId('lap-counter')).toHaveText(`Lap 1 / ${replay.totalLaps}`)

  const events = page.getByRole('list', { name: 'Race events' })
  await events.getByRole('button', { name: new RegExp(`^${sc.summary}`) }).click()

  await expect(page.getByTestId('race-time')).toHaveText(clock(sc.t))
  await expect(page.getByTestId('lap-counter')).toHaveText(`Lap ${sc.lap} / ${replay.totalLaps}`)
  await expect(page.getByRole('slider', { name: 'Race time' })).toHaveValue(String(Math.floor(sc.t / 1000) * 1000))
  await expect(page.locator('canvas')).toHaveAttribute('data-banner', 'SAFETY CAR')
  await expect(events.locator('[aria-current="true"]')).toContainText(sc.summary)
})

test('the scrub bar shows the events as markers that seek too', async ({ page }) => {
  const vsc = timeline.events.find((e) => e.type === 'VSC')!

  await page.goto('/races/2023/39/replay')
  await page.getByRole('button', { name: new RegExp(`marker: ${vsc.summary}`, 'i') }).click()

  await expect(page.getByTestId('race-time')).toHaveText(clock(vsc.t))
  await expect(page.getByTestId('lap-counter')).toHaveText(`Lap ${vsc.lap} / ${replay.totalLaps}`)
})

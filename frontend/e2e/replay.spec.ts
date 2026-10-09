import { expect, test } from '@playwright/test'
import { bahrain2024 } from '../src/test/fixtures/api'
import { ready, squareRace } from '../src/test/fixtures/replay'

// The Phase 1 milestone: open a race and watch it. The API is faked at the network level (no backend
// or live data needed).
test.beforeEach(async ({ page }) => {
  await page.route('**/api/races/1', (route) => route.fulfill({ json: bahrain2024 }))
  await page.route('**/api/races/1/import', (route) => route.fulfill({ json: ready }))
  await page.route('**/api/sessions/5/replay', (route) => route.fulfill({ json: squareRace }))
})

test('open a race, press play: the laps tick by; press pause: it stops', async ({ page }) => {
  await page.goto('/races/2024/1')
  await page.getByRole('link', { name: /watch replay/i }).click()

  const lap = page.getByTestId('lap-counter')
  await expect(lap).toHaveText('Lap 1 / 3')
  await expect(page.locator('canvas')).toBeVisible()

  await page.getByRole('button', { name: 'Play' }).click()
  await expect(lap).toHaveText('Lap 2 / 3', { timeout: 10_000 })

  await page.getByRole('button', { name: 'Pause' }).click()
  const time = page.getByTestId('race-time')
  const pausedAt = await time.textContent()
  await page.waitForTimeout(1_500)
  await expect(time).toHaveText(pausedAt!)
  await expect(lap).toHaveText('Lap 2 / 3')
})

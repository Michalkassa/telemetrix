import { expect, test } from '@playwright/test'
import { bahrain2024 } from '../src/test/fixtures/api'
import { longRace, ready } from '../src/test/fixtures/replay'

// The playback controls: play → 4× → jump to lap 30 → pause, following a driver all the while.
test.beforeEach(async ({ page }) => {
  await page.route('**/api/races/1', (route) => route.fulfill({ json: bahrain2024 }))
  await page.route('**/api/races/1/import', (route) => route.fulfill({ json: ready }))
  await page.route('**/api/sessions/5/replay/snapshots', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/sessions/5/replay', (route) => route.fulfill({ json: longRace() }))
})

test('play, go 4×, jump to lap 30 and pause: the followed driver stays centred', async ({ page }) => {
  await page.goto('/races/2024/1/replay')
  const lap = page.getByTestId('lap-counter')
  await expect(lap).toHaveText('Lap 1 / 40')

  await page.getByRole('button', { name: 'Follow HAM' }).click()
  await page.getByRole('button', { name: 'Play' }).click()
  await page.locator('label', { hasText: /^4×$/ }).click() // the visible label of the 4× radio
  await expect(page.getByRole('radio', { name: '4×' })).toBeChecked()
  await page.getByLabel('Jump to lap').fill('30')
  await page.getByLabel('Jump to lap').press('Enter')
  await page.getByRole('button', { name: 'Pause' }).click()

  await expect(lap).toHaveText('Lap 30 / 40')
  const canvas = page.locator('canvas')
  const [x, y] = (await canvas.getAttribute('data-followed-at'))!.split(',').map(Number)
  expect(Math.abs(x - 400)).toBeLessThan(2) // the 800 × 600 canvas's centre
  expect(Math.abs(y - 300)).toBeLessThan(2)

  // paused: nothing moves
  const time = await page.getByTestId('race-time').textContent()
  await page.waitForTimeout(1_200)
  await expect(page.getByTestId('race-time')).toHaveText(time!)
})

test('keyboard: space plays and pauses, arrows skip 10 s', async ({ page }) => {
  await page.goto('/races/2024/1/replay')
  await expect(page.getByTestId('lap-counter')).toHaveText('Lap 1 / 40')

  await page.keyboard.press('ArrowRight')
  await page.keyboard.press('ArrowRight')
  await expect(page.getByTestId('race-time')).toHaveText('0:20')
  await page.keyboard.press('ArrowLeft')
  await expect(page.getByTestId('race-time')).toHaveText('0:10')
  await page.keyboard.press('Space')
  await expect(page.getByRole('button', { name: 'Pause' })).toBeVisible()
})

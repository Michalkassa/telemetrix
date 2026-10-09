import { defineConfig, devices } from '@playwright/test'

// User journeys in a real browser against the Vite dev server; each test fakes the API with
// page.route, so no backend is needed.
export default defineConfig({
  testDir: './e2e',
  use: { baseURL: 'http://localhost:5173', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: { command: 'npm run dev', url: 'http://localhost:5173', reuseExistingServer: !process.env.CI },
})

import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { server } from './test/server'
import { renderRoute } from './test/render'

test('the app renders', async () => {
  server.use(http.get('/api/seasons', () => HttpResponse.json([{ year: 2024, raceCount: 24 }])))

  renderRoute('/')

  expect(await screen.findByRole('heading', { name: /telemetrix/i })).toBeInTheDocument()
})

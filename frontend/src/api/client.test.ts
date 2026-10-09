import { afterEach, describe, expect, test, vi } from 'vitest'
import { ApiError, getJson, postJson } from './client'

function respond(status: number, body: unknown, contentType = 'application/json') {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(typeof body === 'string' ? body : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': contentType },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

describe('getJson', () => {
  test('returns the parsed JSON body', async () => {
    const fetchMock = respond(200, [{ year: 2024, raceCount: 24 }])

    const seasons = await getJson<{ year: number; raceCount: number }[]>('/api/seasons')

    expect(seasons).toEqual([{ year: 2024, raceCount: 24 }])
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/seasons$/),
      expect.objectContaining({ method: 'GET' }),
    )
  })

  test('throws an ApiError with the status and the problem detail', async () => {
    respond(404, { title: 'Not Found', status: 404, detail: 'Race 42 not found' }, 'application/problem+json')

    const error = await getJson('/api/races/42').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, message: 'Race 42 not found', path: '/api/races/42' })
  })

  test('throws an ApiError when the error body is not JSON', async () => {
    respond(502, '<html>Bad Gateway</html>', 'text/html')

    await expect(getJson('/api/seasons')).rejects.toMatchObject({ status: 502, message: 'HTTP 502' })
  })
})

describe('postJson', () => {
  test('posts and returns the parsed body', async () => {
    const fetchMock = respond(202, { raceId: 7, state: 'IMPORTING' })

    const status = await postJson<{ state: string }>('/api/races/7/import')

    expect(status.state).toBe('IMPORTING')
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/races\/7\/import$/),
      expect.objectContaining({ method: 'POST' }),
    )
  })
})

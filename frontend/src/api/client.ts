// The one place that talks HTTP to the backend. Errors become ApiError with the status and the
// backend's problem-detail message (docs/api.md).

export class ApiError extends Error {
  readonly status: number
  readonly path: string

  constructor(status: number, message: string, path: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.path = path
  }
}

// Empty in development and in Docker: the API is on the same origin (Vite's proxy, nginx).
const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? ''

function url(path: string): string {
  if (BASE_URL) return BASE_URL + path
  // an absolute URL also works where there's no page to be relative to (tests run on Node's fetch)
  return typeof window !== 'undefined' ? new URL(path, window.location.origin).toString() : path
}

async function request<T>(method: 'GET' | 'POST', path: string): Promise<T> {
  const response = await fetch(url(path), { method, headers: { Accept: 'application/json' } })
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response), path)
  }
  return (await response.json()) as T
}

async function problemDetail(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { detail?: string; title?: string }
    return body.detail ?? body.title ?? `HTTP ${response.status}`
  } catch {
    return `HTTP ${response.status}`
  }
}

export function getJson<T>(path: string): Promise<T> {
  return request<T>('GET', path)
}

export function postJson<T>(path: string): Promise<T> {
  return request<T>('POST', path)
}

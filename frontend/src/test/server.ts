import { setupServer } from 'msw/node'

// A fake backend for component tests: each test adds the handlers it needs with server.use(...).
export const server = setupServer()

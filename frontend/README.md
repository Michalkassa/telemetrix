# Telemetrix frontend

React 19 + TypeScript + Vite. Pages and their URLs are in `src/routes.tsx`; every selection lives in
the URL, so every view is a deep link. The backend's API is described in `../docs/api.md`.

```bash
npm install          # first time
npm run dev          # http://localhost:5173 — /api is proxied to the backend on :8080
npm test             # Vitest + Testing Library; MSW fakes the backend
npm run e2e          # Playwright browser journeys (first time: npx playwright install chromium)
npm run build        # type-check and build to dist/
npm run lint         # oxlint
```

Built test-first like the backend (see `../STEPS.md` → How we work):

| Layer | Where | Tools |
|-------|-------|-------|
| Pure logic (replay maths, clock) | `src/**/*.test.ts` | Vitest |
| Components and pages | `src/**/*.test.tsx` | Testing Library, MSW (`src/test/server.ts`) |
| Browser journeys | `e2e/` | Playwright, with recorded API responses |

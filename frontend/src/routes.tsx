import type { RouteObject } from 'react-router'
import { Shell } from './components/Shell'
import { Dashboard } from './pages/Dashboard'
import { Landing } from './pages/Landing'
import { RaceList } from './pages/RaceList'
import { RacePage } from './pages/RacePage'
import { RacesIndex } from './pages/RacesIndex'
import { ReplayPage } from './pages/ReplayPage'
import { ReplayTheater, ReplayTheaterIndex } from './pages/ReplayTheater'

// Every page and its URL (plan.md, "Route tree"). All selection state lives in the URL, so every
// view is a deep link.
export const routes: RouteObject[] = [
  { path: '/', element: <Landing /> },
  {
    element: <Shell />,
    children: [
      { path: '/dashboard', element: <Dashboard /> },
      { path: '/races', element: <RacesIndex /> },
      { path: '/races/:year', element: <RaceList /> },
      { path: '/races/:year/:raceId', element: <RacePage /> },
      { path: '/races/:year/:raceId/replay', element: <ReplayPage /> },
      {
        // its charts (Recharts) load with it, not with the rest of the app
        path: '/races/:year/:raceId/telemetry',
        lazy: () => import('./pages/TelemetryPage').then((m) => ({ Component: m.TelemetryPage })),
      },
      { path: '/replay', element: <ReplayTheaterIndex /> },
      { path: '/replay/:year', element: <ReplayTheater /> },
    ],
  },
]

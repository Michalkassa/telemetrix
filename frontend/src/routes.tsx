import type { RouteObject } from 'react-router'
import { Shell } from './components/Shell'
import { Dashboard } from './pages/Dashboard'
import { Landing } from './pages/Landing'
import { RaceList } from './pages/RaceList'
import { RacePage } from './pages/RacePage'
import { RacesIndex } from './pages/RacesIndex'
import { ReplayPage } from './pages/ReplayPage'
import { ReplayTheater, ReplayTheaterIndex } from './pages/ReplayTheater'
import { ResultsPage } from './pages/ResultsPage'
import { TimelinePage } from './pages/TimelinePage'
import { ResultsGridPage } from './pages/seasons/ResultsGridPage'
import { SeasonHub } from './pages/seasons/SeasonHub'
import { SeasonRacesPage } from './pages/seasons/SeasonRacesPage'
import { SeasonsIndex } from './pages/seasons/SeasonsIndex'
import { ConstructorStandingsPage, DriverStandingsPage } from './pages/seasons/StandingsPages'
import { DriversDirectory, TeamsDirectory } from './pages/competitors/Directories'
import { DriverRacesPage, DriverSeasonsPage, DriverStatsPage } from './pages/competitors/DriverPages'
import { TeamOverviewPage, TeamReliabilityPage } from './pages/competitors/TeamPages'

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
      { path: '/races/:year/:raceId/results', element: <ResultsPage /> },
      { path: '/races/:year/:raceId/timeline', element: <TimelinePage /> },
      {
        path: '/races/:year/:raceId/laps',
        lazy: () => import('./pages/LapPacePage').then((m) => ({ Component: m.LapPacePage })),
      },
      {
        // its charts (Recharts) load with it, not with the rest of the app
        path: '/races/:year/:raceId/telemetry',
        lazy: () => import('./pages/TelemetryPage').then((m) => ({ Component: m.TelemetryPage })),
      },
      { path: '/seasons', element: <SeasonsIndex /> },
      { path: '/seasons/:year', element: <SeasonHub /> },
      { path: '/seasons/:year/standings/drivers', element: <DriverStandingsPage /> },
      { path: '/seasons/:year/standings/constructors', element: <ConstructorStandingsPage /> },
      { path: '/seasons/:year/results', element: <ResultsGridPage /> },
      { path: '/seasons/:year/races', element: <SeasonRacesPage /> },
      {
        path: '/seasons/:year/compare',
        lazy: () => import('./pages/seasons/ComparePage').then((m) => ({ Component: m.ComparePage })),
      },
      { path: '/drivers', element: <DriversDirectory /> },
      { path: '/drivers/:ref', element: <DriverStatsPage /> },
      { path: '/drivers/:ref/seasons', element: <DriverSeasonsPage /> },
      { path: '/drivers/:ref/races', element: <DriverRacesPage /> },
      { path: '/teams', element: <TeamsDirectory /> },
      { path: '/teams/:ref', element: <TeamOverviewPage /> },
      { path: '/teams/:ref/reliability', element: <TeamReliabilityPage /> },
      {
        path: '/teams/:ref/pace',
        lazy: () => import('./pages/competitors/TeamPacePage').then((m) => ({ Component: m.TeamPacePage })),
      },
      { path: '/replay', element: <ReplayTheaterIndex /> },
      { path: '/replay/:year', element: <ReplayTheater /> },
    ],
  },
]

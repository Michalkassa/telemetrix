import type { RouteObject } from 'react-router'
import { Home } from './pages/Home'

// Every page and its URL. All selection state lives in the URL, so every view is a deep link.
export const routes: RouteObject[] = [{ path: '/', element: <Home /> }]

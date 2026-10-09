import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { routes } from './routes'

const queryClient = new QueryClient({
  // data from the backend changes rarely (history); don't refetch on every window focus
  defaultOptions: { queries: { staleTime: 60_000, refetchOnWindowFocus: false } },
})

const router = createBrowserRouter(routes)

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
}

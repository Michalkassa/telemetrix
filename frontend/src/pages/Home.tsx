import { useSeasons } from '../api/queries'

// Step 16's smoke page: proves the frontend reaches the backend. Replaced by the landing page in Step 17.
export function Home() {
  const seasons = useSeasons()
  return (
    <main className="mx-auto max-w-3xl p-8">
      <h1 className="text-3xl font-bold">Telemetrix</h1>
      {seasons.isPending && <p>Loading seasons…</p>}
      {seasons.isError && <p role="alert">Could not load seasons: {seasons.error.message}</p>}
      {seasons.data && <p>{seasons.data.length} seasons available.</p>}
    </main>
  )
}

import { Link, useParams } from 'react-router'
import { useRace } from '../api/queries'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { formatDate, formatTime } from '../components/format'

// /races/:year/:raceId — one race weekend. Becomes the full race hub in Step 29.
export function RacePage() {
  const params = useParams()
  const year = Number(params.year)
  const race = useRace(Number(params.raceId))

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: 'Dashboard', to: '/dashboard' },
          { label: 'Grand Prix', to: '/races' },
          { label: String(year), to: `/races/${year}` },
          { label: race.data?.name ?? '…' },
        ]}
      />
      {race.isPending && <p className="text-zinc-400">Loading race…</p>}
      {race.isError && (
        <p role="alert" className="rounded-md border border-red-900 bg-red-950 p-4 text-red-200">
          {race.error.message}
        </p>
      )}
      {race.data && (
        <>
          <header>
            <h1 className="text-3xl font-bold">{race.data.name}</h1>
            <p className="mt-1 text-zinc-400">
              Round {race.data.round} · {race.data.circuit.name}
              {race.data.circuit.locality && `, ${race.data.circuit.locality}`} · {formatDate(race.data.date)}
            </p>
          </header>

          {race.data.openf1MeetingKey !== null ? (
            <Link
              to={`/races/${year}/${race.data.id}/replay`}
              className="inline-block rounded-full bg-red-600 px-6 py-2.5 font-semibold text-white hover:bg-red-500"
            >
              ▶ Watch replay
            </Link>
          ) : (
            <p className="text-zinc-400">
              Replays start in 2023 — for this race there are results and history only.
            </p>
          )}

          {race.data.sessions.length > 0 && (
            <section>
              <h2 className="mb-2 text-lg font-semibold">Sessions</h2>
              <ul className="divide-y divide-zinc-800 rounded-xl border border-zinc-800">
                {race.data.sessions.map((s) => (
                  <li key={s.id} className="flex justify-between px-4 py-2">
                    <span>{s.name}</span>
                    <span className="text-sm text-zinc-400">{formatTime(s.startsAt)}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  )
}

import { Link, useParams } from 'react-router'
import { useRace, useRaceResults } from '../api/queries'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { formatDate, formatTime } from '../components/format'

// /races/:year/:raceId — one race weekend: the hub for its pages (plan.md, Grand Prix lens). Results
// come from the history (1950 on) and lap times from 1996; the replay, timeline and telemetry need
// OpenF1, which starts in 2023 — before that they're shown greyed out, with why.
const FIRST_LAP_TIMES = 1996

interface Leaf {
  name: string
  path: string
  about: string
  available: boolean
}

export function RacePage() {
  const params = useParams()
  const year = Number(params.year)
  const raceId = Number(params.raceId)
  const race = useRace(raceId)
  const results = useRaceResults(raceId)
  const openF1 = race.data?.openf1MeetingKey != null
  const base = `/races/${year}/${raceId}`

  const leaves: Leaf[] = [
    { name: 'Results', path: 'results', about: 'The classification', available: true },
    { name: 'Timeline', path: 'timeline', about: 'Overtakes, pit stops, flags, incidents', available: openF1 },
    { name: 'Lap pace', path: 'laps', about: 'Every lap time, driver by driver', available: year >= FIRST_LAP_TIMES },
    { name: 'Telemetry', path: 'telemetry', about: 'Speed, throttle and brake, lap against lap', available: openF1 },
    { name: 'Watch replay', path: 'replay', about: 'The race on the track map', available: openF1 },
  ]
  const podium = (results.data?.results ?? []).filter((r) => r.position !== null && r.position <= 3)

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

          {podium.length > 0 && (
            <ol aria-label="Podium" className="flex flex-wrap gap-3">
              {podium.map((r) => (
                <li key={r.position} className="rounded-xl border border-zinc-800 bg-zinc-900 px-4 py-2">
                  <span className="mr-2 text-zinc-500">P{r.position}</span>
                  <span className="font-semibold">
                    {r.givenName} {r.surname}
                  </span>
                  <span className="ml-2 text-sm text-zinc-400">{r.team}</span>
                </li>
              ))}
            </ol>
          )}

          <ul aria-label="Race pages" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            {leaves.map((leaf) => (
              <li
                key={leaf.path}
                aria-disabled={leaf.available ? undefined : 'true'}
                className={`rounded-xl border p-4 ${leaf.available ? 'border-zinc-800 bg-zinc-900 hover:border-red-600' : 'border-zinc-900 text-zinc-600'}`}
              >
                {leaf.available ? (
                  <Link to={`${base}/${leaf.path}`} className="block">
                    <span className="block font-semibold">{leaf.name}</span>
                    <span className="mt-1 block text-sm text-zinc-400">{leaf.about}</span>
                  </Link>
                ) : (
                  <>
                    <span className="block font-semibold">{leaf.name}</span>
                    <span className="mt-1 block text-sm">{leaf.about}</span>
                  </>
                )}
              </li>
            ))}
          </ul>
          {!openF1 && (
            <p className="text-zinc-400">
              Replays start in 2023, and so do the timeline and telemetry (they need OpenF1's timing). For this race
              there are results{year >= FIRST_LAP_TIMES ? ' and lap times' : ''}.
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

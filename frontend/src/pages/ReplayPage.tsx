import type { ReactNode } from 'react'
import { useParams } from 'react-router'
import { useRace, useRaceImport, useReplay } from '../api/queries'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ReplayPlayer } from '../replay/ReplayPlayer'

// /races/:year/:raceId/replay — watch the race. Opening it downloads the race's data the first time
// (~10 s), then the whole replay loads at once and plays in the browser.
export function ReplayPage() {
  const params = useParams()
  const year = Number(params.year)
  const raceId = Number(params.raceId)
  const race = useRace(raceId)
  const opened = useRaceImport(raceId)
  const ready = opened.data?.state === 'READY'
  const session = race.data?.sessions.find((s) => s.name === 'Race')
  const replay = useReplay(ready ? session?.id : undefined)

  let body: ReactNode
  if (race.isError || opened.isError || replay.isError) {
    body = <Problem>{(race.error ?? opened.error ?? replay.error)?.message}</Problem>
  } else if (opened.data?.state === 'FAILED') {
    body = <Problem>Downloading the race data failed: {opened.data.message ?? 'unknown error'}</Problem>
  } else if (opened.data?.state === 'NOT_AVAILABLE') {
    body = <Problem>There is no replay for this race — replays start in 2023.</Problem>
  } else if (race.data && ready && !session) {
    body = <Problem>This weekend has no race session to replay.</Problem>
  } else if (replay.data) {
    body = <ReplayPlayer replay={replay.data} />
  } else if (opened.data?.state === 'IMPORTING') {
    body = <p className="text-zinc-400">Downloading race data… this takes about ten seconds, once.</p>
  } else {
    body = <p className="text-zinc-400">Loading replay…</p>
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: 'Dashboard', to: '/dashboard' },
          { label: 'Grand Prix', to: '/races' },
          { label: String(year), to: `/races/${year}` },
          { label: race.data?.name ?? '…', to: `/races/${year}/${raceId}` },
          { label: 'Replay' },
        ]}
      />
      {race.data && <h1 className="text-3xl font-bold">{race.data.name}</h1>}
      {body}
    </div>
  )
}

function Problem({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="rounded-md border border-red-900 bg-red-950 p-4 text-red-200">
      {children}
    </p>
  )
}

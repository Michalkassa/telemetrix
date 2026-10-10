import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { Link, useLocation, useParams, useSearchParams } from 'react-router'
import { postJson } from '../api/client'
import { useRace, useRaceImport, useReplay, useSnapshots, useTimeline } from '../api/queries'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ReplayPlayer } from '../replay/ReplayPlayer'
import { openTopic } from '../watch/channel'
import { saveHostKey, useWatch } from '../watch/useWatch'

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
  const snapshots = useSnapshots(replay.data ? session?.id : undefined)
  const timeline = useTimeline(replay.data ? raceId : undefined)
  useTimelineUpdates(raceId, session?.id)
  const [searchParams, setSearchParams] = useSearchParams()
  const watchId = searchParams.get('watch')
  const watch = useWatch(watchId)
  const location = useLocation()
  const [starting, setStarting] = useState(false)

  // the creator of a watch session hosts it (in this tab); everyone with the link follows
  const watchTogether = async (sessionId: number) => {
    setStarting(true)
    try {
      const created = await postJson<{ watchId: string; hostKey: string }>('/api/watch', { sessionId })
      saveHostKey(created.watchId, created.hostKey)
      setSearchParams({ watch: created.watchId })
    } finally {
      setStarting(false)
    }
  }

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
    const sessionId = replay.data.sessionId
    body = (
      <div className="space-y-4">
        <ReplayPlayer
          key={watchId ?? 'solo'}
          replay={replay.data}
          snapshots={snapshots.data}
          events={timeline.data?.events}
          watch={watch}
        />
        {/* in its own window, the telemetry's cursor follows this watch session's clock */}
        <Link
          to={`/races/${year}/${raceId}/telemetry${watchId ? `?watch=${watchId}` : ''}`}
          target="_blank"
          rel="noopener"
          className="mr-3 inline-block rounded-full border border-zinc-700 px-4 py-2 text-sm hover:bg-zinc-800"
        >
          Telemetry ↗
        </Link>
        {watchId ? (
          <label className="flex flex-wrap items-center gap-2 text-sm text-zinc-400">
            Share this link to watch together
            <input
              readOnly
              value={`${window.location.origin}${location.pathname}?watch=${watchId}`}
              onFocus={(e) => e.currentTarget.select()}
              className="min-w-0 flex-1 rounded-md border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-200"
            />
          </label>
        ) : (
          <button
            type="button"
            disabled={starting}
            onClick={() => void watchTogether(sessionId)}
            className="rounded-full border border-zinc-700 px-4 py-2 text-sm hover:bg-zinc-800 disabled:opacity-50"
          >
            Watch together
          </button>
        )}
      </div>
    )
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

// The server pushes when a race's data was imported again (a race still being run): fetch the timeline,
// replay and snapshots again (docs/adr/0005).
function useTimelineUpdates(raceId: number, sessionId: number | undefined) {
  const queryClient = useQueryClient()
  useEffect(() => {
    const topic = openTopic(`/topic/races/${raceId}/timeline`, () => {
      void queryClient.invalidateQueries({ queryKey: ['races', raceId, 'timeline'] })
      if (sessionId !== undefined) void queryClient.invalidateQueries({ queryKey: ['sessions', sessionId] })
    })
    return () => topic.close()
  }, [queryClient, raceId, sessionId])
}

function Problem({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="rounded-md border border-red-900 bg-red-950 p-4 text-red-200">
      {children}
    </p>
  )
}

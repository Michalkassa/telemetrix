import { useNavigate } from 'react-router'
import { useTimeline } from '../api/queries'
import { EventList } from '../timeline/EventList'
import { RaceLeaf } from './RaceLeaf'

// /races/:year/:raceId/timeline — the race's events; clicking one opens the replay at that moment.
export function TimelinePage() {
  const navigate = useNavigate()
  return (
    <RaceLeaf title="Timeline">
      {(raceId, year) => <Events raceId={raceId} onOpen={(t) => navigate(`/races/${year}/${raceId}/replay?t=${t}`)} />}
    </RaceLeaf>
  )
}

function Events({ raceId, onOpen }: { raceId: number; onOpen: (t: number) => void }) {
  const timeline = useTimeline(raceId)
  if (timeline.isPending) return <p className="text-zinc-400">Loading the timeline… (open the race first)</p>
  if (timeline.isError) return <p role="alert">{timeline.error.message}</p>
  return <EventList events={timeline.data.events} time={-Infinity} onSeek={onOpen} />
}

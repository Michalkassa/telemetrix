import type { ReactNode } from 'react'
import { useParams } from 'react-router'
import { useRace } from '../api/queries'
import { Breadcrumbs } from '../components/Breadcrumbs'

// The frame of a race hub's leaf: breadcrumbs back up to the race, and its title.
export function RaceLeaf({
  title,
  children,
}: {
  title: string
  children: (raceId: number, year: number) => ReactNode
}) {
  const params = useParams()
  const year = Number(params.year)
  const raceId = Number(params.raceId)
  const race = useRace(raceId)
  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: 'Dashboard', to: '/dashboard' },
          { label: 'Grand Prix', to: '/races' },
          { label: String(year), to: `/races/${year}` },
          { label: race.data?.name ?? '…', to: `/races/${year}/${raceId}` },
          { label: title },
        ]}
      />
      <h1 className="text-3xl font-bold">
        {race.data?.name ?? 'Race'} · <span className="text-zinc-400">{title}</span>
      </h1>
      {children(raceId, year)}
    </div>
  )
}

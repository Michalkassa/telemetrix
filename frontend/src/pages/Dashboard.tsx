import { Link } from 'react-router'
import { Breadcrumbs } from '../components/Breadcrumbs'

interface Lens {
  title: string
  description: string
  to?: string // not built yet: no link
}

// The four ways into the data (plan.md, "Top-level lenses").
const LENSES: Lens[] = [
  { title: 'Seasons', description: 'A championship year: standings and every result.', to: '/seasons' },
  { title: 'Grand Prix', description: 'One race weekend: results, timeline, telemetry and replay.', to: '/races' },
  { title: 'Drivers & Teams', description: 'Careers, team seasons, reliability and pace.', to: '/drivers' },
  { title: 'Replay Theater', description: 'Pick a race and watch it unfold on the track map.', to: '/replay' },
]

export function Dashboard() {
  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: 'Dashboard' }]} />
      <div className="grid gap-4 sm:grid-cols-2">
        {LENSES.map((lens) => (
          <article key={lens.title} className="relative rounded-xl border border-zinc-800 bg-zinc-900 p-6">
            <h2 className="text-xl font-semibold">
              {lens.to ? (
                <Link to={lens.to} className="after:absolute after:inset-0 hover:text-red-400">
                  {lens.title}
                </Link>
              ) : (
                lens.title
              )}
            </h2>
            <p className="mt-2 text-zinc-400">{lens.description}</p>
            {!lens.to && <p className="mt-4 text-xs uppercase tracking-wider text-zinc-500">Coming soon</p>}
          </article>
        ))}
      </div>
    </div>
  )
}

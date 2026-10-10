import type { ReactNode } from 'react'
import { useParams } from 'react-router'
import { Breadcrumbs } from '../../components/Breadcrumbs'

// The frame of a season page: breadcrumbs back up through the Seasons lens, and its title.
export function SeasonLeaf({ title, children }: { title?: string; children: (year: number) => ReactNode }) {
  const year = Number(useParams().year)
  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: 'Dashboard', to: '/dashboard' },
          { label: 'Seasons', to: '/seasons' },
          title ? { label: String(year), to: `/seasons/${year}` } : { label: String(year) },
          ...(title ? [{ label: title }] : []),
        ]}
      />
      <h1 className="text-3xl font-bold">
        {year} season{title && <span className="text-zinc-400"> · {title}</span>}
      </h1>
      {children(year)}
    </div>
  )
}

export function Loading({ what }: { what: string }) {
  return <p className="text-zinc-400">Loading {what}…</p>
}

export function Problem({ error }: { error: Error }) {
  return (
    <p role="alert" className="rounded-md border border-red-900 bg-red-950 p-4 text-red-200">
      {error.message}
    </p>
  )
}

import { Link } from 'react-router'

export interface Crumb {
  label: string
  to?: string // the current page has none
}

// Where you are in the drill-down, and the way back up.
export function Breadcrumbs({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label="Breadcrumb" className="text-sm text-zinc-400">
      <ol className="flex flex-wrap items-center gap-2">
        {items.map((item, i) => (
          <li key={item.label} className="flex items-center gap-2">
            {i > 0 && <span aria-hidden="true">›</span>}
            {item.to ? (
              <Link to={item.to} className="hover:text-zinc-100 hover:underline">
                {item.label}
              </Link>
            ) : (
              <span aria-current="page" className="text-zinc-100">
                {item.label}
              </span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  )
}

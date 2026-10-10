import type { ReactNode } from 'react'
import { NavLink } from 'react-router'

// Headline numbers: a label, then its value (screen readers read them in that order).
export function Figures({ label, items }: { label: string; items: [string, ReactNode][] }) {
  return (
    <ul aria-label={label} className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {items.map(([name, value]) => (
        <li key={name} className="rounded-xl border border-zinc-800 bg-zinc-900 p-3">
          <span className="block text-xs uppercase tracking-wider text-zinc-500">{name}</span>
          <span className="mt-1 block text-xl font-semibold tabular-nums">{value}</span>
        </li>
      ))}
    </ul>
  )
}

export function Tabs({ label, items }: { label: string; items: [string, string][] }) {
  return (
    <nav aria-label={label} className="flex flex-wrap gap-2 text-sm">
      {items.map(([to, name]) => (
        <TabLink key={to} to={to}>
          {name}
        </TabLink>
      ))}
    </nav>
  )
}

function TabLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        `rounded-full border px-4 py-1.5 ${isActive ? 'border-red-600 text-zinc-100' : 'border-zinc-700 text-zinc-400 hover:text-zinc-100'}`
      }
    >
      {children}
    </NavLink>
  )
}

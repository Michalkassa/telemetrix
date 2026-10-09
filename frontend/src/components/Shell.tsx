import { Link, Outlet } from 'react-router'

// The frame around every page after the landing page.
export function Shell() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-zinc-800">
        <div className="mx-auto flex max-w-6xl items-center gap-6 px-6 py-3">
          <Link to="/" className="font-bold tracking-wide text-red-500">
            TELEMETRIX
          </Link>
          <Link to="/dashboard" className="text-sm text-zinc-400 hover:text-zinc-100">
            Dashboard
          </Link>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  )
}

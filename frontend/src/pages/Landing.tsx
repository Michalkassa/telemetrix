import { Link } from 'react-router'

export function Landing() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 px-6 text-center">
      <h1 className="text-5xl font-black tracking-tight sm:text-7xl">
        <span className="text-red-500">TELE</span>METRIX
      </h1>
      <p className="max-w-xl text-lg text-zinc-400">
        Every Formula 1 race since 2023, replayed as live on the track map — and 75 years of results, standings
        and history.
      </p>
      <Link
        to="/dashboard"
        className="rounded-full bg-red-600 px-8 py-3 font-semibold text-white transition hover:bg-red-500"
      >
        Enter Dashboard
      </Link>
    </main>
  )
}

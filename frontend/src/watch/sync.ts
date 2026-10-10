// Following a watch session's shared clock (docs/adr/0005). The server says "race time positionMs at
// server time `at`, moving at `speed` while playing"; to turn that into race time now we need the
// server's time now, so we estimate how far our clock is from the server's.

export interface ClockState {
  watchId: string
  sessionId: number
  seq: number // changes, numbered in order
  playing: boolean
  speed: number
  positionMs: number // race time (ms after the replay's clockStart) at `at`
  at: string // server time
  serverTime: string // when the server sent this
}

/** Race time at a moment on the server's clock (ms since the epoch). */
export function raceTimeAt(state: ClockState, serverNow: number): number {
  if (!state.playing) return state.positionMs
  return state.positionMs + (serverNow - Date.parse(state.at)) * state.speed
}

export class ClockSync {
  state: ClockState | null = null
  // server time − our time. A message's delay makes the server look behind, so the largest is the best.
  private offset = -Infinity

  /** Takes a state received at `localNow`; false (and ignored) if it's older than one we have. */
  observe(state: ClockState, localNow: number): boolean {
    this.offset = Math.max(this.offset, Date.parse(state.serverTime) - localNow)
    if (this.state && state.seq < this.state.seq) return false
    this.state = state
    return true
  }

  serverNow(localNow: number): number {
    return Number.isFinite(this.offset) ? localNow + this.offset : localNow
  }

  /** Race time now, or null before the first state. */
  raceTime(localNow: number): number | null {
    return this.state ? raceTimeAt(this.state, this.serverNow(localNow)) : null
  }
}

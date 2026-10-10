import { createStore } from 'zustand/vanilla'

// The replay player's state (Zustand, one store per player): the playback clock — like a video's
// (docs/adr/0004), race time in ms after the session's clockStart — and who the camera follows or keeps
// in focus. The clock is anchored: race time `anchorTime` at wall time `anchorWall`, moving at `speed`
// while playing. Wall time (`now`, e.g. performance.now()) is always passed in, so it's testable.

export const SPEEDS = [0.25, 0.5, 1, 2, 4, 8]

export interface PlayerState {
  duration: number // race time of the last timing point
  playing: boolean
  speed: number
  anchorTime: number
  anchorWall: number
  follow: number | null // the car the camera follows
  focus: number[] // the cars the camera keeps in view
  play(now: number): void
  pause(now: number): void
  toggle(now: number): void
  seek(ms: number, now: number): void
  skip(deltaMs: number, now: number): void
  setSpeed(speed: number, now: number): void
  faster(now: number): void
  slower(now: number): void
  toggleFollow(car: number): void
  toggleFocus(car: number): void
  clearFocus(): void
}

/** Race time at wall time `now`, within the race. */
export function timeAt(state: PlayerState, now: number): number {
  const t = state.playing ? state.anchorTime + (now - state.anchorWall) * state.speed : state.anchorTime
  return clamp(t, 0, state.duration)
}

/** `startAt`: race time to begin at (a link to a moment). */
export function createPlayerStore(duration: number, startAt = 0) {
  return createStore<PlayerState>((set, get) => {
    // re-anchors the clock at `now` (where it is), then applies the change
    const at = (now: number, change: Partial<PlayerState>) =>
      set({ anchorTime: timeAt(get(), now), anchorWall: now, ...change })
    const step = (now: number, by: number) => {
      const i = SPEEDS.indexOf(get().speed)
      at(now, { speed: SPEEDS[clamp(i + by, 0, SPEEDS.length - 1)] })
    }
    return {
      duration,
      playing: false,
      speed: 1,
      anchorTime: clamp(startAt, 0, duration),
      anchorWall: 0,
      follow: null,
      focus: [],
      play: (now) => at(now, { playing: true }),
      pause: (now) => at(now, { playing: false }),
      toggle: (now) => at(now, { playing: !get().playing }),
      seek: (ms, now) => set({ anchorTime: clamp(ms, 0, duration), anchorWall: now }),
      skip: (deltaMs, now) => set({ anchorTime: clamp(timeAt(get(), now) + deltaMs, 0, duration), anchorWall: now }),
      setSpeed: (speed, now) => {
        if (SPEEDS.includes(speed)) at(now, { speed })
      },
      faster: (now) => step(now, 1),
      slower: (now) => step(now, -1),
      toggleFollow: (car) => set({ follow: get().follow === car ? null : car }),
      toggleFocus: (car) => {
        const focus = get().focus
        set({ focus: focus.includes(car) ? focus.filter((c) => c !== car) : [...focus, car] })
      },
      clearFocus: () => set({ focus: [] }),
    }
  })
}

export type PlayerStore = ReturnType<typeof createPlayerStore>

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

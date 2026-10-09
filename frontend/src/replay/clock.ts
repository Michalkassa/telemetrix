// The replay's clock, like a video's (docs/adr/0004): race time in ms after the session's clockStart.
// While playing it advances by wall time elapsed × speed. The wall time (`now`, e.g.
// performance.now()) is always passed in, so it's testable without waiting.
export class PlaybackClock {
  playing = false
  readonly end: number
  readonly speed: number
  private startedAtTime = 0 // race time when play was pressed (or when paused)
  private startedAtNow = 0 // wall time when play was pressed

  constructor({ end, speed = 1 }: { end: number; speed?: number }) {
    this.end = end
    this.speed = speed
  }

  play(now: number) {
    if (this.playing) return
    this.startedAtNow = now
    this.playing = true
  }

  pause(now: number) {
    if (!this.playing) return
    this.startedAtTime = this.timeAt(now)
    this.playing = false
  }

  timeAt(now: number): number {
    if (!this.playing) return this.startedAtTime
    return Math.min(this.end, this.startedAtTime + (now - this.startedAtNow) * this.speed)
  }
}

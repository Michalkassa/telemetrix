import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { squareRace } from '../test/fixtures/replay'
import type { Replay, Snapshot, SnapshotCar, Timeline } from '../api/types'
import singaporeReplay from '../test/fixtures/singapore2023/replay.json'
import singaporeTimeline from '../test/fixtures/singapore2023/timeline.json'
import { ClockSync, type ClockState } from '../watch/sync'
import { ReplayPlayer } from './ReplayPlayer'

// jsdom has no canvas: a fake 2D context records what is drawn. The wall clock and the animation
// frames are driven by the test, so nothing waits.
let ctx: Record<string, ReturnType<typeof vi.fn>>
let wall: number
let frames: FrameRequestCallback[]

beforeEach(() => {
  ctx = Object.fromEntries(
    ['clearRect', 'fillRect', 'beginPath', 'moveTo', 'lineTo', 'closePath', 'stroke', 'arc', 'fill', 'fillText'].map(
      (name) => [name, vi.fn()],
    ),
  )
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as unknown as CanvasRenderingContext2D)
  wall = 0
  frames = []
})

afterEach(() => vi.restoreAllMocks())

function renderPlayer() {
  return render(
    <ReplayPlayer replay={squareRace} now={() => wall} requestFrame={(cb) => frames.push(cb)} cancelFrame={() => {}} />,
  )
}

// Moves the wall clock on and runs the animation frames waiting for it.
function tick(ms: number) {
  wall += ms
  const pending = frames
  frames = []
  act(() => pending.forEach((cb) => cb(wall)))
}

// The centres of the dots drawn since the last check: [x, y] per arc.
function dots() {
  const centres = ctx.arc.mock.calls.map(([x, y]) => [Math.round(x as number), Math.round(y as number)])
  ctx.arc.mockClear()
  return centres
}

// The 1000 × 1000 square in the 800 × 600 canvas with 40 px padding: scale 0.52, the line at (140, 560).
test('draws the track and one dot per car, on the grid before the start', () => {
  renderPlayer()
  tick(0)

  expect(ctx.lineTo).toHaveBeenCalled() // the outline
  expect(dots()).toEqual([
    [140, 560],
    [140, 560],
  ])
  expect(screen.getByTestId('lap-counter')).toHaveTextContent('Lap 1 / 3')
})

test('play moves the dots along the track; pause freezes them', async () => {
  renderPlayer()
  tick(0)
  dots()

  await userEvent.click(screen.getByRole('button', { name: 'Play' }))
  tick(1000) // VER a quarter round (1000, 0), HAM an eighth (500, 0) — the leader is drawn last, on top
  expect(dots()).toEqual([
    [400, 560],
    [660, 560],
  ])

  await userEvent.click(screen.getByRole('button', { name: 'Pause' }))
  tick(5000)
  expect(dots()).toEqual([
    [400, 560],
    [660, 560],
  ])
  expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()
})

test('the lap counter and the running order follow the race', async () => {
  renderPlayer()
  await userEvent.click(screen.getByRole('button', { name: 'Play' }))
  tick(6000) // VER 1.5 laps done, HAM 0.75

  expect(screen.getByTestId('lap-counter')).toHaveTextContent('Lap 2 / 3')
  expect(screen.getByTestId('race-time')).toHaveTextContent('0:06')
  const rows = screen.getAllByRole('listitem').map((li) => li.textContent)
  expect(rows).toEqual([expect.stringMatching(/^1\s*VER/), expect.stringMatching(/^2\s*HAM/)])
})

describe('watching together', () => {
  const epoch = Date.parse('2026-10-09T20:00:00Z')

  function shared(over: Partial<ClockState>): ClockState {
    const iso = new Date(epoch).toISOString()
    return {
      watchId: 'w1',
      sessionId: 5,
      seq: 1,
      playing: false,
      speed: 1,
      positionMs: 0,
      at: iso,
      serverTime: iso,
      ...over,
    }
  }

  function renderWatching(role: 'host' | 'viewer', sync: ClockSync, send = vi.fn()) {
    render(
      <ReplayPlayer
        replay={squareRace}
        now={() => wall}
        epochNow={() => epoch + wall}
        requestFrame={(cb) => frames.push(cb)}
        cancelFrame={() => {}}
        watch={{ role, sync, send }}
      />,
    )
    return send
  }

  test('a viewer follows the shared clock and has no controls', () => {
    const sync = new ClockSync()
    sync.observe(shared({ playing: true, positionMs: 4_000 }), epoch)
    renderWatching('viewer', sync)

    tick(2_000) // 2 s later on the shared clock: race time 6 s

    expect(screen.getByTestId('race-time')).toHaveTextContent('0:06')
    expect(screen.getByTestId('lap-counter')).toHaveTextContent('Lap 2 / 3')
    expect(screen.queryByRole('button', { name: 'Play' })).not.toBeInTheDocument()
    expect(screen.getByText(/following the host/i)).toBeInTheDocument()
  })

  test("the host's play is a command; the clock moves when the server says so", async () => {
    const sync = new ClockSync()
    sync.observe(shared({}), epoch)
    const send = renderWatching('host', sync)

    await userEvent.click(screen.getByRole('button', { name: 'Play' }))
    tick(3_000)

    expect(send).toHaveBeenCalledWith({ action: 'PLAY' })
    expect(screen.getByTestId('race-time')).toHaveTextContent('0:00') // nothing until the server answers

    sync.observe(shared({ seq: 2, playing: true, at: new Date(epoch + 3_000).toISOString() }), epoch + 3_000)
    tick(2_000)
    expect(screen.getByTestId('race-time')).toHaveTextContent('0:02')
    expect(screen.getByRole('button', { name: 'Pause' })).toBeInTheDocument()
  })
})

describe('following and focus', () => {
  test('following a car keeps it in the centre of the canvas', async () => {
    renderPlayer()
    await userEvent.click(screen.getByRole('button', { name: 'Follow HAM' }))
    await userEvent.click(screen.getByRole('button', { name: 'Play' }))
    tick(1000)

    expect(screen.getByRole('button', { name: 'Follow HAM' })).toHaveAttribute('aria-pressed', 'true')
    const [x, y] = document.querySelector('canvas')!.getAttribute('data-followed-at')!.split(',').map(Number)
    expect(x).toBeCloseTo(400, 6)
    expect(y).toBeCloseTo(300, 6)
    expect(dots()).toContainEqual([400, 300]) // HAM's dot, drawn there
  })

  test('cars in focus are toggled on and off', async () => {
    renderPlayer()
    await userEvent.click(screen.getByRole('button', { name: 'Focus VER' }))
    await userEvent.click(screen.getByRole('button', { name: 'Focus HAM' }))
    await userEvent.click(screen.getByRole('button', { name: 'Focus VER' }))

    expect(screen.getByRole('button', { name: 'Focus VER' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: 'Focus HAM' })).toHaveAttribute('aria-pressed', 'true')
  })
})

describe('the controls drive the clock', () => {
  test('a jump to a lap shows that lap', async () => {
    renderPlayer()
    await userEvent.type(screen.getByLabelText('Jump to lap'), '3{Enter}')
    tick(0)

    expect(screen.getByTestId('lap-counter')).toHaveTextContent('Lap 3 / 3')
    expect(screen.getByTestId('race-time')).toHaveTextContent('0:08')
  })

  test('the speed makes it go faster', async () => {
    renderPlayer()
    await userEvent.click(screen.getByRole('radio', { name: '4×' }))
    await userEvent.click(screen.getByRole('button', { name: 'Play' }))
    tick(1000)

    expect(screen.getByTestId('race-time')).toHaveTextContent('0:04')
  })

  test("a host's seek and speed go to the shared clock", async () => {
    const epoch = Date.parse('2026-10-09T20:00:00Z')
    const sync = new ClockSync()
    const iso = new Date(epoch).toISOString()
    sync.observe(
      { watchId: 'w1', sessionId: 5, seq: 1, playing: false, speed: 1, positionMs: 0, at: iso, serverTime: iso },
      epoch,
    )
    const send = vi.fn()
    render(
      <ReplayPlayer
        replay={squareRace}
        now={() => wall}
        epochNow={() => epoch + wall}
        requestFrame={(cb) => frames.push(cb)}
        cancelFrame={() => {}}
        watch={{ role: 'host', sync, send }}
      />,
    )

    await userEvent.click(screen.getByRole('radio', { name: '2×' }))
    await userEvent.type(screen.getByLabelText('Jump to lap'), '2{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'Forward 10 s' }))

    expect(send.mock.calls).toEqual([
      [{ action: 'SPEED', value: 2 }],
      [{ action: 'SEEK', value: 4000 }],
      [{ action: 'SEEK', value: 10_000 }], // from where the shared clock is (0) — and never past the end
    ])
  })
})

describe('overlays', () => {
  const snapshot = (over: Partial<Snapshot>): Snapshot => ({
    t: 0,
    lap: 1,
    reason: 'CHANGE',
    flag: 'GREEN',
    yellowSectors: {},
    drsEnabled: false,
    cars: {},
    ...over,
  })
  const car = (over: Partial<SnapshotCar>): SnapshotCar => ({
    compound: 'MEDIUM',
    stintLapStart: 1,
    ageAtStart: 0,
    pitStops: 0,
    inPit: false,
    penaltySeconds: 0,
    pendingPenalties: 0,
    compounds: ['MEDIUM'],
    ...over,
  })
  const withSectors = {
    ...squareRace,
    layout: {
      ...squareRace.layout!,
      marshalSectors: [
        { number: 1, progress: 0 },
        { number: 2, progress: 0.5 },
      ],
    },
  }

  function renderWith(snapshots: Snapshot[], replay = withSectors) {
    render(
      <ReplayPlayer
        replay={replay}
        snapshots={snapshots}
        now={() => wall}
        requestFrame={(cb) => frames.push(cb)}
        cancelFrame={() => {}}
      />,
    )
  }

  test('the safety car: a banner, and the SC drawn ahead of the leader', () => {
    renderWith([snapshot({ flag: 'SAFETY_CAR' })])
    tick(0)

    const canvas = document.querySelector('canvas')!
    expect(canvas).toHaveAttribute('data-banner', 'SAFETY CAR')
    expect(ctx.fillText).toHaveBeenCalledWith('SAFETY CAR', expect.any(Number), expect.any(Number))
    expect(ctx.fillText).toHaveBeenCalledWith('SC', expect.any(Number), expect.any(Number))
  })

  test('yellow marshalling sectors are highlighted, as race control says', () => {
    renderWith([snapshot({ yellowSectors: { '2': 'DOUBLE_YELLOW' } })])
    tick(0)

    expect(document.querySelector('canvas')).toHaveAttribute('data-yellow', '2')
    expect(document.querySelector('canvas')).not.toHaveAttribute('data-banner')
  })

  test('the leaderboard: gap to the car ahead, tyres and their age, DRS, penalties', async () => {
    renderWith([
      snapshot({
        drsEnabled: true,
        cars: { '1': car({}), '44': car({ compound: 'HARD', stintLapStart: 1, ageAtStart: 3, penaltySeconds: 5 }) },
      }),
    ])
    await userEvent.click(screen.getByRole('button', { name: 'Play' }))
    tick(6000) // HAM 3 s behind VER

    const [ver, ham] = screen.getAllByRole('listitem')
    expect(ver).toHaveTextContent('Leader')
    expect(ver).toHaveTextContent('M1') // mediums, lap 2 of them... age 1
    expect(ham).toHaveTextContent('+3.0')
    expect(ham).toHaveTextContent('H3') // hards fitted 3 laps old, still on lap 1
    expect(ham).toHaveTextContent('+5s')
    expect(ham).not.toHaveTextContent('DRS') // 3 s behind: too far
  })

  test('a retired car is out, not laps down', async () => {
    renderWith([])
    await userEvent.click(screen.getByRole('button', { name: 'Play' }))
    tick(9000) // HAM stopped at 8 s

    const ham = screen.getAllByRole('listitem').find((li) => li.textContent?.includes('HAM'))!
    expect(ham).toHaveTextContent('OUT')
    expect(ham).not.toHaveTextContent('lap')
  })

  test('the weather', () => {
    renderWith([], {
      ...withSectors,
      weather: [
        { t: -1000, airTemperature: 30.1, trackTemperature: 38.4, humidity: 68, rainfall: false, windSpeed: 0.8 },
      ],
    })
    tick(0)

    expect(screen.getByTestId('weather')).toHaveTextContent('Air 30°')
    expect(screen.getByTestId('weather')).toHaveTextContent('Track 38°')
  })
})

describe('the timeline when watching together', () => {
  // Singapore 2023, as the API serves it
  const events = (singaporeTimeline as Timeline).events
  const sc = events.find((e) => e.type === 'SAFETY_CAR')!
  const epoch = Date.parse('2026-10-09T20:00:00Z')

  function renderWatching(role: 'host' | 'viewer', send = vi.fn()) {
    const sync = new ClockSync()
    const iso = new Date(epoch).toISOString()
    sync.observe(
      { watchId: 'w1', sessionId: 15, seq: 1, playing: false, speed: 1, positionMs: 0, at: iso, serverTime: iso },
      epoch,
    )
    render(
      <ReplayPlayer
        replay={singaporeReplay as unknown as Replay} // JSON reads [x, y] pairs as number[]
        events={events}
        now={() => wall}
        epochNow={() => epoch + wall}
        requestFrame={(cb) => frames.push(cb)}
        cancelFrame={() => {}}
        watch={{ role, sync, send }}
      />,
    )
    return send
  }

  test("the host's click on an event moves everyone's clock there", async () => {
    const send = renderWatching('host')

    await userEvent.click(
      within(screen.getByRole('list', { name: 'Race events' })).getByRole('button', {
        name: new RegExp(`^${sc.summary}`),
      }),
    )

    expect(send).toHaveBeenCalledWith({ action: 'SEEK', value: sc.t })
  })

  test("a viewer's list follows but can't move the shared clock", () => {
    renderWatching('viewer')

    expect(
      within(screen.getByRole('list', { name: 'Race events' })).getByRole('button', {
        name: new RegExp(`^${sc.summary}`),
      }),
    ).toBeDisabled()
  })
})

import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import type { Timeline } from '../api/types'
import recorded from '../test/fixtures/singapore2023/timeline.json'
import { Controls, type PlaybackControls } from './Controls'

// Each control calls the right action; keyboard shortcuts do the same.
let actions: { [K in keyof PlaybackControls]: ReturnType<typeof vi.fn> }

beforeEach(() => {
  actions = { toggle: vi.fn(), seek: vi.fn(), skip: vi.fn(), setSpeed: vi.fn() }
})

function renderControls(over: Partial<Parameters<typeof Controls>[0]> = {}) {
  return render(
    <Controls
      controls={actions as unknown as PlaybackControls}
      playing={false}
      speed={1}
      time={65_000}
      duration={600_000}
      totalLaps={40}
      lapStart={(lap) => (lap - 1) * 10_000}
      {...over}
    />,
  )
}

describe('buttons', () => {
  test('play / pause', async () => {
    renderControls()
    await userEvent.click(screen.getByRole('button', { name: 'Play' }))
    expect(actions.toggle).toHaveBeenCalledOnce()
  })

  test('shows Pause while playing', () => {
    renderControls({ playing: true })
    expect(screen.getByRole('button', { name: 'Pause' })).toBeInTheDocument()
  })

  test('rewind and fast forward skip 10 s', async () => {
    renderControls()
    await userEvent.click(screen.getByRole('button', { name: 'Back 10 s' }))
    await userEvent.click(screen.getByRole('button', { name: 'Forward 10 s' }))
    expect(actions.skip.mock.calls).toEqual([[-10_000], [10_000]])
  })

  test('the speed, 0.25× to 8×', async () => {
    renderControls()
    expect(screen.getByRole('radio', { name: '1×' })).toBeChecked()
    expect(screen.getAllByRole('radio').map((r) => r.getAttribute('aria-label'))).toEqual([
      '0.25×',
      '0.5×',
      '1×',
      '2×',
      '4×',
      '8×',
    ])
    await userEvent.click(screen.getByRole('radio', { name: '4×' }))
    expect(actions.setSpeed).toHaveBeenCalledWith(4)
  })
})

describe('jumping', () => {
  test('to a lap: where the leader started it', async () => {
    renderControls()
    await userEvent.type(screen.getByLabelText('Jump to lap'), '30{Enter}')
    expect(actions.seek).toHaveBeenCalledWith(290_000)
  })

  test('to a race time, m:ss or h:mm:ss', async () => {
    renderControls()
    await userEvent.type(screen.getByLabelText('Jump to time'), '1:05{Enter}')
    await userEvent.clear(screen.getByLabelText('Jump to time'))
    await userEvent.type(screen.getByLabelText('Jump to time'), '1:02:03{Enter}')
    expect(actions.seek.mock.calls).toEqual([[65_000], [3_723_000]])
  })

  test('nonsense is ignored', async () => {
    renderControls()
    await userEvent.type(screen.getByLabelText('Jump to time'), 'soon{Enter}')
    await userEvent.type(screen.getByLabelText('Jump to lap'), '0{Enter}')
    expect(actions.seek).not.toHaveBeenCalled()
  })

  test('the timeline bar shows where we are and scrubs', () => {
    renderControls()
    const bar = screen.getByRole('slider', { name: 'Race time' })
    expect(bar).toHaveValue('65000')
    expect(bar).toHaveAttribute('max', '600000')

    fireEvent.change(bar, { target: { value: '120000' } })
    expect(actions.seek).toHaveBeenCalledWith(120_000)
  })
})

describe('keyboard', () => {
  test('space plays or pauses; arrows skip and change speed', async () => {
    renderControls({ speed: 2 })
    await userEvent.keyboard(' ')
    await userEvent.keyboard('{ArrowLeft}{ArrowRight}')
    await userEvent.keyboard('{ArrowUp}{ArrowDown}')

    expect(actions.toggle).toHaveBeenCalledOnce()
    expect(actions.skip.mock.calls).toEqual([[-10_000], [10_000]])
    expect(actions.setSpeed.mock.calls).toEqual([[4], [1]])
  })

  test('not while typing in a box', async () => {
    renderControls()
    await userEvent.click(screen.getByLabelText('Jump to lap'))
    await userEvent.keyboard(' {ArrowLeft}')
    expect(actions.toggle).not.toHaveBeenCalled()
    expect(actions.skip).not.toHaveBeenCalled()
  })
})

describe('event markers on the timeline bar', () => {
  // Singapore 2023's events, as the API serves them
  const events = (recorded as Timeline).events
  const duration = Math.max(...events.map((e) => e.t))

  test('clicking a marker seeks to its event', async () => {
    const sc = events.find((e) => e.type === 'SAFETY_CAR')!
    renderControls({ duration, events })

    await userEvent.click(screen.getByRole('button', { name: `Marker: ${sc.summary}, lap ${sc.lap}` }))

    expect(actions.seek).toHaveBeenCalledWith(sc.t)
  })

  test('no markers without events', () => {
    renderControls()
    expect(screen.queryAllByRole('button', { name: /^marker/i })).toHaveLength(0)
  })
})

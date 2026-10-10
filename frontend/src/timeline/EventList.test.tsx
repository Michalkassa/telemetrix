import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import type { Timeline } from '../api/types'
import recorded from '../test/fixtures/singapore2023/timeline.json'
import { EventList } from './EventList'

// The race's events as a list (Singapore 2023, as the API serves it): click one to seek there.
const timeline = recorded as Timeline
const events = timeline.events
const sc = events.find((e) => e.type === 'SAFETY_CAR')!

function list() {
  return screen.getByRole('list', { name: 'Race events' })
}

test('clicking an event seeks to it', async () => {
  const seek = vi.fn()
  render(<EventList events={events} time={0} onSeek={seek} />)

  await userEvent.click(within(list()).getByRole('button', { name: new RegExp(`^${sc.summary}`) }))

  expect(seek).toHaveBeenCalledWith(sc.t)
})

test('the current event is highlighted for the clock', () => {
  const { rerender } = render(<EventList events={events} time={sc.t} onSeek={() => {}} />)

  expect(
    within(list())
      .getAllByRole('listitem')
      .filter((li) => li.getAttribute('aria-current') === 'true'),
  ).toHaveLength(1)
  expect(list().querySelector('[aria-current="true"]')).toHaveTextContent(sc.summary)

  rerender(<EventList events={events} time={events[0].t - 1} onSeek={() => {}} />)
  expect(list().querySelector('[aria-current="true"]')).toBeNull()
})

test('filters hide event types', async () => {
  render(<EventList events={events} time={0} onSeek={() => {}} />)
  const overtakes = events.filter((e) => e.type === 'OVERTAKE').length
  const before = within(list()).getAllByRole('listitem').length
  expect(before).toBe(events.length)

  await userEvent.click(screen.getByRole('checkbox', { name: /overtakes/i }))

  expect(within(list()).getAllByRole('listitem')).toHaveLength(before - overtakes)
  expect(within(list()).queryByText(events.find((e) => e.type === 'OVERTAKE')!.summary)).not.toBeInTheDocument()
  expect(screen.getByRole('checkbox', { name: /overtakes/i })).not.toBeChecked()
})

test('each event shows its lap, and team radio links to the recording', () => {
  render(<EventList events={events} time={0} onSeek={() => {}} />)
  const radio = events.find((e) => e.type === 'TEAM_RADIO' && e.link)!

  expect(within(list()).getByRole('button', { name: new RegExp(`^${sc.summary}`) })).toHaveTextContent(`L${sc.lap}`)
  expect(within(list()).getAllByRole('link', { name: /listen/i })[0]).toHaveAttribute('href', radio.link!)
})

test('a championship lead change has its own filter', () => {
  const lead = {
    ...sc,
    type: 'CHAMPIONSHIP_LEAD',
    summary: 'Championship lead: HAM takes it from VER (if the race finished now)',
  }
  render(<EventList events={[lead]} time={0} onSeek={() => {}} />)

  expect(screen.getByRole('checkbox', { name: /championship lead/i })).toBeChecked()
})

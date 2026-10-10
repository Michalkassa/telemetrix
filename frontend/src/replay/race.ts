import type { Replay } from '../api/types'
import { progressAt, reachedAt, timeline, type Timeline } from './progress'
import { outline, type Outline } from './track'

// A replay ready to play: each car's timeline and the track outline, built once when it loads.

export interface RaceCar {
  number: number
  code: string
  colour: string
  timeline: Timeline
}

export interface Race {
  totalLaps: number
  duration: number // ms from clockStart to the last timing point
  cars: RaceCar[]
  track: Outline | null
}

/** Where a car is at one moment. */
export interface CarNow {
  number: number
  code: string
  colour: string
  position: number
  lap: number // the lap it's on
  lapsDone: number
}

export function prepare(replay: Replay): Race {
  const drivers = new Map(replay.drivers.map((d) => [d.number, d]))
  const cars = replay.timing.map((car) => {
    const driver = drivers.get(car.number)
    return {
      number: car.number,
      code: driver?.code ?? String(car.number),
      colour: driver?.teamColour ? `#${driver.teamColour}` : '#a1a1aa',
      timeline: timeline(car.t, car.laps),
    }
  })
  return {
    totalLaps: replay.totalLaps,
    duration: Math.max(0, ...cars.map((c) => c.timeline.t.at(-1) ?? 0)),
    cars,
    track: replay.layout ? outline(replay.layout.points) : null,
  }
}

/** Every car at time t, furthest round the race first (level cars keep their order). */
export function runningOrder(race: Race, t: number): CarNow[] {
  return race.cars
    .map((car) => ({ car, lapsDone: progressAt(car.timeline, t) }))
    .sort((a, b) => b.lapsDone - a.lapsDone)
    .map(({ car, lapsDone }, i) => ({
      number: car.number,
      code: car.code,
      colour: car.colour,
      position: i + 1,
      lap: lapOf(race, lapsDone),
      lapsDone,
    }))
}

/** The lap the leader is on at time t. */
export function leaderLap(race: Race, t: number): number {
  return lapOf(race, Math.max(0, ...race.cars.map((car) => progressAt(car.timeline, t))))
}

function lapOf(race: Race, lapsDone: number): number {
  return Math.max(1, Math.min(Math.floor(lapsDone) + 1, race.totalLaps))
}

/** When the leader started a lap (race time, ms): the first car to have done the laps before it. */
export function lapStart(race: Race, lap: number): number {
  const target = Math.min(Math.max(lap, 1), race.totalLaps) - 1
  const times = race.cars.map((car) => reachedAt(car.timeline, target)).filter((t): t is number => t !== null)
  return times.length ? Math.min(...times) : 0
}

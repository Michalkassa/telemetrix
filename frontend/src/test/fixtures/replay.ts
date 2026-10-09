import type { ImportStatus, Replay } from '../../api/types'

// A tiny race you can work out by hand: a 1000 × 1000 square track (anticlockwise from the line at
// (0, 0)), 3 laps. VER laps in 4 s; HAM is slower and stops after a lap.
export const squareRace: Replay = {
  sessionId: 5,
  raceId: 1,
  session: 'Race',
  clockStart: '2024-03-02T15:03:00Z',
  chequeredAt: '2024-03-02T15:03:12Z',
  totalLaps: 3,
  layout: {
    circuit: 'square',
    year: 2024,
    rotation: 0,
    length: 4000,
    boundingBox: { minX: 0, minY: 0, maxX: 1000, maxY: 1000 },
    points: [
      [0, 0],
      [1000, 0],
      [1000, 1000],
      [0, 1000],
    ],
    startFinish: [0, 0],
    sectorEnds: [0.25, 0.75],
  },
  drivers: [
    { number: 1, code: 'VER', name: 'Max VERSTAPPEN', team: 'Red Bull Racing', teamColour: '3671c6' },
    { number: 44, code: 'HAM', name: 'Lewis HAMILTON', team: 'Mercedes', teamColour: '27f4d2' },
  ],
  timing: [
    { number: 1, t: [0, 1000, 4000, 8000, 12000], laps: [0, 0.25, 1, 2, 3], pits: [], finished: true, retiredAt: null },
    { number: 44, t: [0, 2000, 8000], laps: [0, 0.25, 1], pits: [], finished: false, retiredAt: 8000 },
  ],
}

export const ready: ImportStatus = { raceId: 1, state: 'READY', since: '2024-03-03T10:00:00Z', message: null }

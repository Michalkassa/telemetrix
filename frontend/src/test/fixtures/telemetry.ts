import type { LapAnalysis, LapComparison, TracePoint } from '../../api/types'

// Two tiny laps, shaped like GET .../telemetry/lap and .../telemetry/compare (Bahrain 2024's fastest).
const point = (time: number, distance: number, speed: number, throttle = 100, brake = 0): TracePoint => ({
  time,
  distance,
  speed,
  throttle,
  brake,
  gear: 7,
  rpm: 11_000,
  drs: 0,
})

export const verLap39: LapAnalysis = {
  sessionId: 5,
  driver: 1,
  lap: 39,
  startsAt: '2024-03-02T16:05:28.544Z',
  lapTime: 92.608,
  sectors: [29.741, 39.916, 22.951],
  tyre: { compound: 'SOFT', stint: 3, age: 1 },
  estimates: {
    fuelKg: 36.7,
    ersDeployedMj: 4,
    ersHarvestedMj: 2,
    note: 'Estimates from simple models, not measured data',
  },
  distance: 5400,
  trace: [point(0, 0, 290), point(40_000, 2400, 120, 0, 100), point(92_608, 5400, 300)],
}

export const lecLap36: LapAnalysis = {
  ...verLap39,
  driver: 16,
  lap: 36,
  startsAt: '2024-03-02T16:01:12.318Z',
  lapTime: 94.09,
  sectors: [30.063, 40.65, 23.377],
  tyre: { compound: 'HARD', stint: 3, age: 2 },
  trace: [point(0, 0, 285), point(41_000, 2400, 118, 0, 100), point(94_090, 5400, 296)],
}

export const verVsLec: LapComparison = {
  reference: verLap39,
  other: lecLap36,
  lapDelta: 1.482,
  sectorDeltas: [0.322, 0.734, 0.426],
  delta: [
    { distance: 0, delta: 0 },
    { distance: 2400, delta: 0.8 },
    { distance: 5400, delta: 1.482 },
  ],
}

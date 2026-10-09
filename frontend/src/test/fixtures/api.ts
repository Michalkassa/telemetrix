import type { RaceDetail, SeasonRaces } from '../../api/types'

// Small, realistic API answers for component tests (shapes from docs/api.md).

export const seasons = [
  { year: 2025, raceCount: 0 },
  { year: 2024, raceCount: 3 },
  { year: 2023, raceCount: 2 },
]

const bahrain = { ref: 'bahrain', name: 'Bahrain International Circuit', locality: 'Sakhir', country: 'Bahrain' }
const jeddah = { ref: 'jeddah', name: 'Jeddah Corniche Circuit', locality: 'Jeddah', country: 'Saudi Arabia' }
const albert = { ref: 'albert_park', name: 'Albert Park Grand Prix Circuit', locality: 'Melbourne', country: 'Australia' }

export const races2024: SeasonRaces = {
  year: 2024,
  races: [
    { id: 1, round: 1, name: 'Bahrain Grand Prix', date: '2024-03-02', circuit: bahrain, hasOpenF1Data: true },
    { id: 2, round: 2, name: 'Saudi Arabian Grand Prix', date: '2024-03-09', circuit: jeddah, hasOpenF1Data: true },
    { id: 3, round: 3, name: 'Australian Grand Prix', date: '2024-03-24', circuit: albert, hasOpenF1Data: true },
  ],
}

export const races2023: SeasonRaces = {
  year: 2023,
  races: [
    { id: 11, round: 1, name: 'Bahrain Grand Prix', date: '2023-03-05', circuit: bahrain, hasOpenF1Data: true },
    { id: 12, round: 2, name: 'Saudi Arabian Grand Prix', date: '2023-03-19', circuit: jeddah, hasOpenF1Data: true },
  ],
}

export const bahrain2024: RaceDetail = {
  id: 1,
  season: 2024,
  round: 1,
  name: 'Bahrain Grand Prix',
  date: '2024-03-02',
  circuit: bahrain,
  openf1MeetingKey: 1229,
  importedAt: null,
  sessions: [
    { id: 4, name: 'Qualifying', startsAt: '2024-03-01T16:00:00Z', endsAt: '2024-03-01T17:00:00Z' },
    { id: 5, name: 'Race', startsAt: '2024-03-02T15:00:00Z', endsAt: '2024-03-02T17:00:00Z' },
  ],
}

export const britishGp1950: RaceDetail = {
  id: 900,
  season: 1950,
  round: 1,
  name: 'British Grand Prix',
  date: '1950-05-13',
  circuit: { ref: 'silverstone', name: 'Silverstone Circuit', locality: 'Silverstone', country: 'UK' },
  openf1MeetingKey: null,
  importedAt: null,
  sessions: [],
}

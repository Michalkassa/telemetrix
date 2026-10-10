-- A tiny, hand-checkable dataset for the driver analytics (Step 32): 3 races, 4 drivers.
--
--   race 1  2024 round 1  Bahrain   WET (rain in the race)    race 2  2024 round 2  Monaco  DRY
--   race 3  2025 round 1  Bahrain   DRY
--
--   (grid → finish; R = retired, DNS = did not start)
--   HAM  1→1 FL   |  4→2       |  2→1
--   VER  3→2      |  1→1 FL    |  1→2 FL
--   LEC  2→3      |  2→R       |  DNS
--   BOT  4→R      |  3→3       |  4→3
--
-- Worked out by hand — Hamilton: 3 starts, 2 wins, 3 podiums, 0 DNFs, 1 pole (grid 1), 1 fastest lap,
-- average finish (1+2+1)/3 = 1.333, positions gained 0+2+1 = 3 (1.0 a race), 26+18+25 = 69 points; wet: 1 race,
-- 1 win, avg 1.0; dry: 2 races, 1 win, avg 1.5. Bahrain: 2 starts, 2 wins, best 1; Monaco: 1 start,
-- 0 wins, best 2. Pit stops: 3 in 2 races (1.5 a race), lane times 22.0 + 23.0 + 25.0 s → avg 23.333 s; the
-- 2048.376 s "stop" in race 2 is a red-flag stoppage (the cars wait in the pit lane: Silverstone 2021 in the
-- Jolpica data), not a pit stop.
-- Tyres (race 1): SOFT laps 1-20 (20 laps), HARD laps 21-57 (37 laps) → 2 stints, 57 laps, 28.5 a stint.
-- Leclerc: 2 starts (the DNS isn't one), 0 wins, 1 podium, 1 DNF, average finish 3.0.
-- Verstappen: 2 poles, 2 fastest laps, positions gained +1 +0 −1 = 0.

INSERT INTO seasons (year) VALUES (2024), (2025);
INSERT INTO circuits (id, ref, name) OVERRIDING SYSTEM VALUE VALUES
    (1, 'bahrain', 'Bahrain International Circuit'), (2, 'monaco', 'Circuit de Monaco');
INSERT INTO drivers (id, ref, code, given_name, family_name) OVERRIDING SYSTEM VALUE VALUES
    (1, 'hamilton', 'HAM', 'Lewis', 'Hamilton'), (2, 'max_verstappen', 'VER', 'Max', 'Verstappen'),
    (3, 'leclerc', 'LEC', 'Charles', 'Leclerc'), (4, 'bottas', 'BOT', 'Valtteri', 'Bottas');
INSERT INTO constructors (id, ref, name) OVERRIDING SYSTEM VALUE VALUES
    (1, 'mercedes', 'Mercedes'), (2, 'red_bull', 'Red Bull'), (3, 'ferrari', 'Ferrari');
INSERT INTO races (id, season_year, round, circuit_id, name, race_date) OVERRIDING SYSTEM VALUE VALUES
    (1, 2024, 1, 1, 'Bahrain Grand Prix', '2024-03-02'),
    (2, 2024, 2, 2, 'Monaco Grand Prix', '2024-05-26'),
    (3, 2025, 1, 1, 'Bahrain Grand Prix', '2025-04-13');
INSERT INTO sessions (id, race_id, name, starts_at, ends_at) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 'Race', '2024-03-02T15:00:00Z', '2024-03-02T17:00:00Z'),
    (2, 2, 'Race', '2024-05-26T13:00:00Z', '2024-05-26T15:00:00Z'),
    (3, 3, 'Race', '2025-04-13T15:00:00Z', '2025-04-13T17:00:00Z');

-- race entries: (race, driver) → id = race * 10 + driver
INSERT INTO race_entries (id, race_id, driver_id, constructor_id, car_number) OVERRIDING SYSTEM VALUE VALUES
    (11, 1, 1, 1, 44), (12, 1, 2, 2, 1), (13, 1, 3, 3, 16), (14, 1, 4, 1, 77),
    (21, 2, 1, 1, 44), (22, 2, 2, 2, 1), (23, 2, 3, 3, 16), (24, 2, 4, 1, 77),
    (31, 3, 1, 1, 44), (32, 3, 2, 2, 1), (33, 3, 3, 3, 16), (34, 3, 4, 1, 77);

INSERT INTO session_results (race_entry_id, session_type, session_number, position, grid, points, laps_completed,
                             status, status_detail, classified, fastest_lap_rank) VALUES
    (11, 'R', 1, 1, 1, 26, 57, 'FINISHED', 'Finished', true, 1),
    (12, 'R', 1, 2, 3, 18, 57, 'FINISHED', 'Finished', true, 2),
    (13, 'R', 1, 3, 2, 15, 57, 'FINISHED', 'Finished', true, 3),
    (14, 'R', 1, 4, 4, 0, 30, 'RETIRED_MECHANICAL', 'Engine', false, 4),
    (21, 'R', 1, 2, 4, 18, 78, 'FINISHED', 'Finished', true, 2),
    (22, 'R', 1, 1, 1, 26, 78, 'FINISHED', 'Finished', true, 1),
    (23, 'R', 1, 4, 2, 0, 10, 'RETIRED_INCIDENT', 'Accident', false, null),
    (24, 'R', 1, 3, 3, 15, 78, 'FINISHED', 'Finished', true, 3),
    (31, 'R', 1, 1, 2, 25, 57, 'FINISHED', 'Finished', true, 2),
    (32, 'R', 1, 2, 1, 19, 57, 'FINISHED', 'Finished', true, 1),
    (33, 'R', 1, 4, 0, 0, 0, 'DID_NOT_START', 'Did not start', false, null),
    (34, 'R', 1, 3, 4, 15, 57, 'FINISHED', 'Finished', true, 3);

-- Hamilton's pit stops (history): one in race 1, two in race 2, and a red flag in race 2
INSERT INTO pit_stops (race_entry_id, stop_number, lap, lane_duration_ms) VALUES
    (11, 1, 20, 22000), (21, 1, 25, 23000), (21, 2, 30, 2048376), (21, 3, 50, 25000);

-- Hamilton's tyres in race 1 (OpenF1 stints, by car number)
INSERT INTO tire_stints (session_id, driver_number, stint_number, compound, lap_start, lap_end, tyre_age_at_start) VALUES
    (1, 44, 1, 'SOFT', 1, 20, 0), (1, 44, 2, 'HARD', 21, 57, 0);

-- the weather: rain during race 1; races 2 and 3 dry
INSERT INTO weather_data (session_id, recorded_at, rainfall) VALUES
    (1, '2024-03-02T15:10:00Z', false), (1, '2024-03-02T15:40:00Z', true),
    (2, '2024-05-26T13:30:00Z', false), (3, '2025-04-13T15:30:00Z', false);

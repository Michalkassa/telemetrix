-- A tiny, hand-checkable dataset for the comparisons (Step 35): Monza 2023 and 2024, Bahrain 2024; VER, LEC,
-- SAI. Lap times are each race's whole distance — four laps at Monza, three in Bahrain (seconds below).
--
-- Monza 2023 (race 1; not opened in the app: strategies come from the history's pit stops, no compounds)
--   VER 1st (grid 1) 90 85 84 86          no stop (a red flag on lap 2 kept him in the pit lane 31 min: not a stop)
--   SAI 2nd (grid 2) 91 86 99p 104        stop lap 3 → stints 1-3, 4-4
--   LEC 3rd (grid 3) 92 100p 105 85       stop lap 2 → stints 1-2, 3-4
--   pace (median, winner): 1 → 91/90, 2 → 86/85, 3 → 99/84, 4 → 86/86; fastest lap VER 84.0
--
-- Monza 2024 (race 2, round 16; opened: OpenF1 stints with compounds)
--   LEC 1st (grid 2) 91 84 83 84          HARD 1-4
--   VER 2nd (grid 1) 90 85 88p 103        MEDIUM 1-3, HARD 4-4 (the in-lap is inside 107%: only the pit rule drops it)
--   SAI 3rd (grid 3) 92 99p 101 86        MEDIUM 1-2, HARD 3-4
--   pace: 1 → 91/91, 2 → 85/84, 3 → 88/83, 4 → 86/84; fastest lap LEC 83.0
--
-- Bahrain 2024 (race 3, round 1): VER 1st (grid 1) 100 95 110 (a safety car); LEC R Engine (grid 2) 101 97; SAI 2nd 102 96 97
--
-- VER vs LEC in 2024 — clean laps: not lap 1, not into or out of the pits, within 107% of the race's fastest:
--   Bahrain (fastest 95 → limit 101.65): VER 95 (110 is over) → 95.0, best 95.0; LEC 97 → 97.0; gap −2.0 s
--   Monza (fastest 83):   VER 85    → 85.0, best 85.0;       LEC 84 83 84 → 84.0, best 83.0; gap +1.0 s
--   ahead in the race: Bahrain VER (LEC retired), Monza LEC → 1-1; on the grid: VER both → 2-0
--   points: VER 25 + 18 = 43, LEC 0 + 26 = 26

INSERT INTO seasons (year) VALUES (2023), (2024);
INSERT INTO circuits (id, ref, name) OVERRIDING SYSTEM VALUE VALUES
    (1, 'monza', 'Autodromo Nazionale di Monza'), (2, 'bahrain', 'Bahrain International Circuit');
INSERT INTO drivers (id, ref, code, given_name, family_name) OVERRIDING SYSTEM VALUE VALUES
    (1, 'max_verstappen', 'VER', 'Max', 'Verstappen'), (2, 'leclerc', 'LEC', 'Charles', 'Leclerc'),
    (3, 'sainz', 'SAI', 'Carlos', 'Sainz');
INSERT INTO constructors (id, ref, name) OVERRIDING SYSTEM VALUE VALUES
    (1, 'red_bull', 'Red Bull'), (2, 'ferrari', 'Ferrari');
INSERT INTO races (id, season_year, round, circuit_id, name, race_date) OVERRIDING SYSTEM VALUE VALUES
    (1, 2023, 14, 1, 'Italian Grand Prix', '2023-09-03'),
    (2, 2024, 16, 1, 'Italian Grand Prix', '2024-09-01'),
    (3, 2024, 1, 2, 'Bahrain Grand Prix', '2024-03-02');
INSERT INTO sessions (id, race_id, name, starts_at, ends_at) OVERRIDING SYSTEM VALUE VALUES
    (1, 2, 'Race', '2024-09-01T13:00:00Z', '2024-09-01T14:30:00Z');

-- race entries: (race, driver) → id = race * 10 + driver
INSERT INTO race_entries (id, race_id, driver_id, constructor_id, car_number) OVERRIDING SYSTEM VALUE VALUES
    (11, 1, 1, 1, 1), (12, 1, 2, 2, 16), (13, 1, 3, 2, 55),
    (21, 2, 1, 1, 1), (22, 2, 2, 2, 16), (23, 2, 3, 2, 55),
    (31, 3, 1, 1, 1), (32, 3, 2, 2, 16), (33, 3, 3, 2, 55);

INSERT INTO session_results (race_entry_id, session_type, session_number, position, grid, points, laps_completed,
                             status, status_detail, classified) VALUES
    (11, 'R', 1, 1, 1, 25, 4, 'FINISHED', 'Finished', true),
    (13, 'R', 1, 2, 2, 18, 4, 'FINISHED', 'Finished', true),
    (12, 'R', 1, 3, 3, 15, 4, 'FINISHED', 'Finished', true),
    (22, 'R', 1, 1, 2, 26, 4, 'FINISHED', 'Finished', true),
    (21, 'R', 1, 2, 1, 18, 4, 'FINISHED', 'Finished', true),
    (23, 'R', 1, 3, 3, 15, 4, 'FINISHED', 'Finished', true),
    (31, 'R', 1, 1, 1, 25, 3, 'FINISHED', 'Finished', true),
    (33, 'R', 1, 2, 3, 18, 3, 'FINISHED', 'Finished', true),
    (32, 'R', 1, 3, 2, 0, 2, 'RETIRED_MECHANICAL', 'Engine', false);

INSERT INTO pit_stops (race_entry_id, stop_number, lap, lane_duration_ms) VALUES
    (11, 1, 2, 1900000), (13, 1, 3, 23000), (12, 1, 2, 24000),
    (21, 1, 3, 22000), (23, 1, 2, 23500);

-- Monza 2024's tyres (OpenF1 stints, by car number)
INSERT INTO tire_stints (session_id, driver_number, stint_number, compound, lap_start, lap_end, tyre_age_at_start) VALUES
    (1, 16, 1, 'HARD', 1, 4, 0),
    (1, 1, 1, 'MEDIUM', 1, 3, 0), (1, 1, 2, 'HARD', 4, 4, 0),
    (1, 55, 1, 'MEDIUM', 1, 2, 0), (1, 55, 2, 'HARD', 3, 4, 0);

INSERT INTO lap_times (race_entry_id, session_type, lap, time_ms) VALUES
    (11, 'R', 1, 90000), (11, 'R', 2, 85000), (11, 'R', 3, 84000), (11, 'R', 4, 86000),
    (13, 'R', 1, 91000), (13, 'R', 2, 86000), (13, 'R', 3, 99000), (13, 'R', 4, 104000),
    (12, 'R', 1, 92000), (12, 'R', 2, 100000), (12, 'R', 3, 105000), (12, 'R', 4, 85000),
    (22, 'R', 1, 91000), (22, 'R', 2, 84000), (22, 'R', 3, 83000), (22, 'R', 4, 84000),
    (21, 'R', 1, 90000), (21, 'R', 2, 85000), (21, 'R', 3, 88000), (21, 'R', 4, 103000),
    (23, 'R', 1, 92000), (23, 'R', 2, 99000), (23, 'R', 3, 101000), (23, 'R', 4, 86000),
    (31, 'R', 1, 100000), (31, 'R', 2, 95000), (31, 'R', 3, 110000),
    (32, 'R', 1, 101000), (32, 'R', 2, 97000),
    (33, 'R', 1, 102000), (33, 'R', 2, 96000), (33, 'R', 3, 97000);

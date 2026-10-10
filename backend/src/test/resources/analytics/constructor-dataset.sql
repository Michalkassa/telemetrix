-- A tiny, hand-checkable dataset for the constructor analytics (Step 33): 2024 rounds 1-2 and one 2023
-- race that the 2024 numbers must leave out. Mercedes HAM/RUS, Red Bull VER/PER, Ferrari LEC/SAI.
--
--   (grid → finish; R = retired, DNS = did not start)
--   2024 r1 Bahrain:  VER 1→1  HAM 3→2  LEC 2→3  PER 5→4  RUS 4→R Engine  SAI DNS
--   2024 r2 Monaco:   VER 3→1  SAI 4→2  RUS 5→3  LEC 1→R Accident  HAM 2→R "Retired"  PER 6→R Gearbox
--   2023 r1 Bahrain:  HAM →R Engine (and a 99 s stop): not in 2024's numbers
--   2010 r1 Bahrain:  RUS 3→2 for Mercedes — no pit stops before 2011, so no strategy to tell
--
-- PER's gearbox failed on lap 72 of 78: classified 4th (90% of the distance), but a retirement, not a finish.
--
-- Reliability 2024 — Mercedes: 4 starts, 2 finishes, 2 DNFs: 1 mechanical (Engine), 1 unknown ("Retired" is
-- all the 2023-on data says). Red Bull: 4 starts, 3 finishes, 1 DNF mechanical (Gearbox). Ferrari: 3 starts (the DNS isn't
-- one), 1 DNF incident (Accident).
--
-- Pit stops 2024 (lane s) — Red Bull 21.0 21.5 22.5 30.0 → median 22.0 (1st); Mercedes 22.0 23.0 24.0 → 23.0
-- (2nd; RUS's 2000 s red-flag stoppage isn't a stop); Ferrari 25.0 26.0 → 25.5 (3rd). All nine: median 23.0.
--
-- Race pace — lap times are the first five laps of each race (seconds). Clean laps leave out lap 1, the
-- lap into and out of the pits, and anything over 107% of the race's fastest clean lap.
--   r1 (fastest 90.0 → limit 96.3; stops VER/PER/LEC lap 5, HAM lap 4):
--     VER 100 | 90 91 92 | 93p    PER 101 | 92 93 94 | 95p    → Red Bull 90 91 92 92 93 94    median 92.0
--     HAM  96 (lap 1) | 91 92 | 95p 96out    RUS 103 | 93 130(>107%) 94 94  → Mercedes 91 92 93 94 94  93.0
--     LEC 104 | 94 95 95 | 96p                                  → Ferrari 94 95 95                 95.0
--     gaps: Red Bull 0, Mercedes 93/92 = +1.0869565 %, Ferrari 95/92 = +3.2608696 %
--   r2 (fastest 70.0 → limit 74.9; stops VER/PER lap 3, SAI lap 5, RUS red flag lap 4 + stop lap 5):
--     VER 79 | 70 74p 74out 70.5    PER 82 | 71 75p 75out 71    → Red Bull 70 70.5 71 71      median 70.75
--     HAM 80 | 70 71 (retired)       RUS 81 | 72 73 140p 76p    → Mercedes 70 71 72 73               71.5
--     LEC 83 | 72 (crashed)          SAI 84 | 73 74 73.5 80p    → Ferrari 72 73 73.5 74              73.25
--     gaps: Red Bull 0, Mercedes 71.5/70.75 = +1.0600707 %, Ferrari 73.25/70.75 = +3.5335689 %
--   season (median of the races): Mercedes 1.0735136 %, Ferrari 3.3972192 %, Red Bull 0
--
-- Qualifying pace — each team's best lap in any segment, against the fastest of all:
--   r1: Red Bull 89.0 (VER Q3), Mercedes 89.2 (RUS Q1), Ferrari 89.7 (LEC Q2) → 0, +0.2247191 %, +0.7865169 %
--   r2: Ferrari 69.0, Mercedes 69.2, Red Bull 69.3 → 0, +0.2898551 %, +0.4347826 %
--   season: Mercedes 0.2572871 %, Red Bull 0.2173913 %, Ferrari 0.3932584 %
--
-- Development trend (Mercedes): qualifying 0.2247191 → 0.2898551 (slope +0.0651360 a round), race pace
-- 1.0869565 → 1.0600707 (slope −0.0268858 a round).
--
-- Strategy (finishers; stops without red flags): Mercedes HAM r1 1 stop +1, RUS r2 2 stops +2;
-- Red Bull VER r1 1 stop 0, PER r1 1 stop +1, VER r2 1 stop +2 → 1 stop: 3 finishes, +1.0 a race;
-- Ferrari LEC r1 1 stop −1, SAI r2 1 stop +2 → 1 stop: 2 finishes, +0.5.

INSERT INTO seasons (year) VALUES (2010), (2023), (2024);
INSERT INTO circuits (id, ref, name) OVERRIDING SYSTEM VALUE VALUES
    (1, 'bahrain', 'Bahrain International Circuit'), (2, 'monaco', 'Circuit de Monaco');
INSERT INTO drivers (id, ref, code, given_name, family_name) OVERRIDING SYSTEM VALUE VALUES
    (1, 'hamilton', 'HAM', 'Lewis', 'Hamilton'), (2, 'max_verstappen', 'VER', 'Max', 'Verstappen'),
    (3, 'leclerc', 'LEC', 'Charles', 'Leclerc'), (4, 'russell', 'RUS', 'George', 'Russell'),
    (5, 'perez', 'PER', 'Sergio', 'Pérez'), (6, 'sainz', 'SAI', 'Carlos', 'Sainz');
INSERT INTO constructors (id, ref, name) OVERRIDING SYSTEM VALUE VALUES
    (1, 'mercedes', 'Mercedes'), (2, 'red_bull', 'Red Bull'), (3, 'ferrari', 'Ferrari'), (4, 'williams', 'Williams');
INSERT INTO races (id, season_year, round, circuit_id, name, race_date) OVERRIDING SYSTEM VALUE VALUES
    (1, 2024, 1, 1, 'Bahrain Grand Prix', '2024-03-02'),
    (2, 2024, 2, 2, 'Monaco Grand Prix', '2024-05-26'),
    (3, 2023, 1, 1, 'Bahrain Grand Prix', '2023-03-05'),
    (4, 2010, 1, 1, 'Bahrain Grand Prix', '2010-03-14');

-- race entries: (race, driver) → id = race * 10 + driver
INSERT INTO race_entries (id, race_id, driver_id, constructor_id, car_number) OVERRIDING SYSTEM VALUE VALUES
    (11, 1, 1, 1, 44), (12, 1, 2, 2, 1), (13, 1, 3, 3, 16), (14, 1, 4, 1, 63), (15, 1, 5, 2, 11), (16, 1, 6, 3, 55),
    (21, 2, 1, 1, 44), (22, 2, 2, 2, 1), (23, 2, 3, 3, 16), (24, 2, 4, 1, 63), (25, 2, 5, 2, 11), (26, 2, 6, 3, 55),
    (31, 3, 1, 1, 44),
    (44, 4, 4, 1, 63);

INSERT INTO session_results (race_entry_id, session_type, session_number, position, grid, laps_completed,
                             status, status_detail, classified) VALUES
    (12, 'R', 1, 1, 1, 57, 'FINISHED', 'Finished', true),
    (11, 'R', 1, 2, 3, 57, 'FINISHED', 'Finished', true),
    (13, 'R', 1, 3, 2, 57, 'FINISHED', 'Finished', true),
    (15, 'R', 1, 4, 5, 57, 'FINISHED', 'Finished', true),
    (14, 'R', 1, 5, 4, 40, 'RETIRED_MECHANICAL', 'Engine', false),
    (16, 'R', 1, 6, 0, 0, 'DID_NOT_START', 'Did not start', false),
    (22, 'R', 1, 1, 3, 78, 'FINISHED', 'Finished', true),
    (26, 'R', 1, 2, 4, 78, 'FINISHED', 'Finished', true),
    (24, 'R', 1, 3, 5, 78, 'FINISHED', 'Finished', true),
    (25, 'R', 1, 4, 6, 72, 'RETIRED_MECHANICAL', 'Gearbox', true),
    (21, 'R', 1, 5, 2, 3, 'RETIRED_MECHANICAL', 'Retired', false),
    (23, 'R', 1, 6, 1, 2, 'RETIRED_INCIDENT', 'Accident', false),
    (31, 'R', 1, 20, 1, 10, 'RETIRED_MECHANICAL', 'Engine', false),
    (44, 'R', 1, 2, 3, 49, 'FINISHED', 'Finished', true);

-- qualifying: each segment's best lap (ms)
INSERT INTO session_results (race_entry_id, session_type, session_number, best_lap_ms) VALUES
    (12, 'Q1', 1, 89800), (12, 'Q2', 1, 89500), (12, 'Q3', 1, 89000),
    (15, 'Q1', 1, 90000), (15, 'Q2', 1, 89900), (15, 'Q3', 1, 89600),
    (11, 'Q1', 1, 89500), (11, 'Q2', 1, 89400), (11, 'Q3', 1, 89300),
    (14, 'Q1', 1, 89200), (14, 'Q2', 1, 89900),
    (13, 'Q1', 1, 90100), (13, 'Q2', 1, 89700), (13, 'Q3', 1, 89800),
    (16, 'Q1', 1, 90500),
    (23, 'Q1', 1, 69900), (23, 'Q2', 1, 69600), (23, 'Q3', 1, 69000),
    (22, 'Q1', 1, 69800), (22, 'Q2', 1, 69700), (22, 'Q3', 1, 69300),
    (21, 'Q1', 1, 69900), (21, 'Q2', 1, 69500), (21, 'Q3', 1, 69200),
    (26, 'Q1', 1, 70100), (26, 'Q2', 1, 69800), (26, 'Q3', 1, 69500),
    (24, 'Q1', 1, 70200), (24, 'Q2', 1, 69900), (24, 'Q3', 1, 69400),
    (25, 'Q1', 1, 70300), (25, 'Q2', 1, 70000);

INSERT INTO pit_stops (race_entry_id, stop_number, lap, lane_duration_ms) VALUES
    (12, 1, 5, 21000), (15, 1, 5, 21500), (11, 1, 4, 22000), (13, 1, 5, 25000),
    (22, 1, 3, 22500), (25, 1, 3, 30000), (26, 1, 5, 26000),
    (24, 1, 4, 2000000), (24, 2, 5, 24000), (24, 3, 6, 23000),
    (31, 1, 10, 99000);

-- the first five laps of each race (ms)
INSERT INTO lap_times (race_entry_id, session_type, lap, time_ms) VALUES
    (12, 'R', 1, 100000), (12, 'R', 2, 90000), (12, 'R', 3, 91000), (12, 'R', 4, 92000), (12, 'R', 5, 93000),
    (15, 'R', 1, 101000), (15, 'R', 2, 92000), (15, 'R', 3, 93000), (15, 'R', 4, 94000), (15, 'R', 5, 95000),
    (11, 'R', 1, 96000), (11, 'R', 2, 91000), (11, 'R', 3, 92000), (11, 'R', 4, 95000), (11, 'R', 5, 96000),
    (14, 'R', 1, 103000), (14, 'R', 2, 93000), (14, 'R', 3, 130000), (14, 'R', 4, 94000), (14, 'R', 5, 94000),
    (13, 'R', 1, 104000), (13, 'R', 2, 94000), (13, 'R', 3, 95000), (13, 'R', 4, 95000), (13, 'R', 5, 96000),
    (22, 'R', 1, 79000), (22, 'R', 2, 70000), (22, 'R', 3, 74000), (22, 'R', 4, 74000), (22, 'R', 5, 70500),
    (25, 'R', 1, 82000), (25, 'R', 2, 71000), (25, 'R', 3, 75000), (25, 'R', 4, 75000), (25, 'R', 5, 71000),
    (21, 'R', 1, 80000), (21, 'R', 2, 70000), (21, 'R', 3, 71000),
    (24, 'R', 1, 81000), (24, 'R', 2, 72000), (24, 'R', 3, 73000), (24, 'R', 4, 140000), (24, 'R', 5, 76000),
    (23, 'R', 1, 83000), (23, 'R', 2, 72000),
    (26, 'R', 1, 84000), (26, 'R', 2, 73000), (26, 'R', 3, 74000), (26, 'R', 4, 73500), (26, 'R', 5, 80000);

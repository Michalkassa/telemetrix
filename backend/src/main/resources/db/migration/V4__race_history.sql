-- V4 — Race history from the Jolpica dump (docs/adr/0003), keyed by race entry.
--
-- A race entry is one car in one race: race + driver + constructor + car number. In the 1950s one
-- driver could share several cars in a race, so the driver alone is not unique. Entries are also how
-- sources line up: an OpenF1 driver_number in a race is that race's car_number.

CREATE TABLE race_entries (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    race_id        BIGINT   NOT NULL REFERENCES races (id),
    driver_id      BIGINT   NOT NULL REFERENCES drivers (id),
    constructor_id BIGINT   NOT NULL REFERENCES constructors (id),
    car_number     SMALLINT,          -- missing for a handful of early entries
    CONSTRAINT race_entries_unique UNIQUE NULLS NOT DISTINCT (race_id, driver_id, constructor_id, car_number)
);

CREATE INDEX idx_race_entries_driver_id ON race_entries (driver_id);
CREATE INDEX idx_race_entries_constructor_id ON race_entries (constructor_id);

-- One entry's result in one session of the weekend.
CREATE TABLE session_results (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    race_entry_id    BIGINT       NOT NULL REFERENCES race_entries (id),
    session_type     VARCHAR(5)   NOT NULL, -- R, SR (sprint), Q1-Q3, QB/QA/QO (older formats), SQ1-SQ3, FP1-FP3
    session_number   SMALLINT     NOT NULL, -- older weekends had several sessions of one type
    position         SMALLINT,
    grid             SMALLINT,
    points           NUMERIC(5, 2),          -- half points exist
    laps_completed   SMALLINT,
    time_ms          BIGINT,                  -- total race time
    status           VARCHAR(30),             -- FINISHED, LAPPED, RETIRED_INCIDENT, ... (ResultStatus)
    status_detail    VARCHAR(100),            -- 'Accident', 'Engine', '+1 Lap', ...
    classified       BOOLEAN,
    fastest_lap_rank SMALLINT,
    best_lap_ms      BIGINT,
    UNIQUE (race_entry_id, session_type, session_number)
);

-- Lap-by-lap times in races and sprints (1996 on).
CREATE TABLE lap_times (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    race_entry_id BIGINT     NOT NULL REFERENCES race_entries (id),
    session_type  VARCHAR(5) NOT NULL,
    lap           SMALLINT   NOT NULL CHECK (lap > 0),
    position      SMALLINT,
    time_ms       BIGINT,
    UNIQUE (race_entry_id, session_type, lap)
);

-- Pit stops (2011 on from Jolpica; OpenF1 adds stationary times later).
CREATE TABLE pit_stops (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    race_entry_id    BIGINT   NOT NULL REFERENCES race_entries (id),
    stop_number      SMALLINT NOT NULL CHECK (stop_number > 0),
    lap              SMALLINT,
    lane_duration_ms BIGINT,   -- pit entry to pit exit
    stop_duration_ms BIGINT,   -- stationary time (OpenF1, 2023 on)
    local_time       TIME,
    UNIQUE (race_entry_id, stop_number)
);

-- Qualifying per entry. Knockout era (2006 on): the final position is the position in the last
-- segment reached (Q3, else Q2, else Q1), with each segment's best lap. Older formats (QB/QA/QO,
-- sometimes several sessions) use the best position and lap across them — an approximation.
-- This is the order the cars qualified in, BEFORE grid penalties and exclusions. The official pole
-- sitter is the car starting from grid 1 (session_results: session_type 'R', grid = 1) — e.g.
-- Hungary 2007 (fastest: Alonso, penalised; pole: Hamilton) and 2021 sprint weekends.
CREATE VIEW qualifying AS
WITH segments AS (
    SELECT race_entry_id,
           MIN(position)    FILTER (WHERE session_type = 'Q1')                AS q1_position,
           MIN(best_lap_ms) FILTER (WHERE session_type = 'Q1')                AS q1_ms,
           MIN(position)    FILTER (WHERE session_type = 'Q2')                AS q2_position,
           MIN(best_lap_ms) FILTER (WHERE session_type = 'Q2')                AS q2_ms,
           MIN(position)    FILTER (WHERE session_type = 'Q3')                AS q3_position,
           MIN(best_lap_ms) FILTER (WHERE session_type = 'Q3')                AS q3_ms,
           MIN(position)    FILTER (WHERE session_type IN ('QB', 'QA', 'QO')) AS single_position,
           MIN(best_lap_ms) FILTER (WHERE session_type IN ('QB', 'QA', 'QO')) AS single_ms
    FROM session_results
    WHERE session_type IN ('Q1', 'Q2', 'Q3', 'QB', 'QA', 'QO')
    GROUP BY race_entry_id
)
SELECT e.race_id,
       e.id                                                            AS race_entry_id,
       e.driver_id,
       e.constructor_id,
       COALESCE(s.q3_position, s.q2_position, s.q1_position, s.single_position) AS position,
       COALESCE(s.q1_ms, s.single_ms)                                  AS q1_ms,
       s.q2_ms,
       s.q3_ms
FROM segments s
JOIN race_entries e ON e.id = s.race_entry_id;

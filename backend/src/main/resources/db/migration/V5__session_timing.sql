-- V5 — OpenF1 timing for race and sprint sessions (2023 on): the replay's timing points
-- (docs/adr/0004). Keyed by session + driver_number, OpenF1's own key: the car number in that race,
-- which is race_entries.car_number for the same race once the Jolpica history covers it.
-- Durations are milliseconds.

CREATE TABLE laps (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id    BIGINT      NOT NULL REFERENCES sessions (id),
    driver_number SMALLINT    NOT NULL,
    lap_number    SMALLINT    NOT NULL CHECK (lap_number > 0),
    starts_at     TIMESTAMPTZ,            -- when the car crossed the line to start this lap
    sector1_ms    INTEGER,                -- missing on some laps (lap 1, pit laps): the lap is partial
    sector2_ms    INTEGER,
    sector3_ms    INTEGER,
    lap_ms        INTEGER,
    i1_speed      SMALLINT,               -- speed traps, km/h
    i2_speed      SMALLINT,
    speed_trap    SMALLINT,
    pit_out_lap   BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (session_id, driver_number, lap_number)
);

CREATE TABLE tire_stints (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id        BIGINT      NOT NULL REFERENCES sessions (id),
    driver_number     SMALLINT    NOT NULL,
    stint_number      SMALLINT    NOT NULL,
    compound          VARCHAR(20),          -- SOFT, MEDIUM, HARD, INTERMEDIATE, WET
    lap_start         SMALLINT,
    lap_end           SMALLINT,
    tyre_age_at_start SMALLINT,              -- laps already on the tyres when fitted
    UNIQUE (session_id, driver_number, stint_number)
);

-- OpenF1 pit stops, with stationary times. (pit_stops holds the Jolpica history, by race entry.)
CREATE TABLE session_pit_stops (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id       BIGINT      NOT NULL REFERENCES sessions (id),
    driver_number    SMALLINT    NOT NULL,
    lap_number       SMALLINT,
    entered_at       TIMESTAMPTZ,
    lane_duration_ms INTEGER,    -- pit entry to pit exit
    stop_duration_ms INTEGER     -- stationary (from late 2024)
);

CREATE INDEX idx_session_pit_stops_session ON session_pit_stops (session_id, driver_number);

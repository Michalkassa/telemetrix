-- V9 — What happened in a race or sprint session besides lap times (OpenF1, 2023 on): the weather,
-- every race-control message, the penalties among them, the track status (yellow / SC / VSC / red
-- periods; green is no period) and team radio. The timeline, flag overlays and crash detector are built
-- from these (Steps 22-26). Replaced as a whole when the session is imported again.

CREATE TABLE weather_data (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id         BIGINT           NOT NULL REFERENCES sessions (id),
    recorded_at        TIMESTAMPTZ      NOT NULL,
    air_temperature    DOUBLE PRECISION,           -- °C
    track_temperature  DOUBLE PRECISION,           -- °C
    humidity           DOUBLE PRECISION,           -- %
    pressure           DOUBLE PRECISION,           -- mbar
    rainfall           BOOLEAN,
    wind_speed         DOUBLE PRECISION,           -- m/s
    wind_direction     SMALLINT                    -- degrees
);

CREATE INDEX idx_weather_data_session ON weather_data (session_id, recorded_at);

-- Race-control messages as OpenF1 sends them (flags, safety car, DRS, incidents, penalties, ...).
CREATE TABLE race_events (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id    BIGINT       NOT NULL REFERENCES sessions (id),
    occurred_at   TIMESTAMPTZ  NOT NULL,
    lap_number    SMALLINT,                -- the leader's lap
    category      VARCHAR(30),             -- Flag, SafetyCar, Drs, CarEvent, SessionStatus, Other
    flag          VARCHAR(30),             -- GREEN, YELLOW, DOUBLE YELLOW, CLEAR, RED, CHEQUERED, ...
    scope         VARCHAR(20),             -- Track, Sector, Driver
    sector        SMALLINT,                -- marshalling sector, for a sector flag
    driver_number SMALLINT,
    message       TEXT         NOT NULL
);

CREATE INDEX idx_race_events_session ON race_events (session_id, occurred_at);

-- Penalties handed out during the session, read from the stewards' race-control messages.
CREATE TABLE penalties (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id    BIGINT       NOT NULL REFERENCES sessions (id),
    race_event_id BIGINT       NOT NULL REFERENCES race_events (id),
    driver_number SMALLINT     NOT NULL,
    kind          VARCHAR(15)  NOT NULL CHECK (kind IN ('TIME', 'DRIVE_THROUGH', 'STOP_GO', 'GRID')),
    amount        SMALLINT,                -- seconds (TIME, STOP_GO) or places (GRID)
    reason        TEXT,
    issued_at     TIMESTAMPTZ  NOT NULL,
    lap_number    SMALLINT
);

CREATE INDEX idx_penalties_session ON penalties (session_id, driver_number);

CREATE TABLE track_status (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id BIGINT       NOT NULL REFERENCES sessions (id),
    status     VARCHAR(12)  NOT NULL CHECK (status IN ('YELLOW', 'SAFETY_CAR', 'VSC', 'VSC_ENDING', 'RED')),
    starts_at  TIMESTAMPTZ  NOT NULL,
    ends_at    TIMESTAMPTZ,                -- null: lasted to the end of the data
    start_lap  SMALLINT,                   -- the leader's lap
    end_lap    SMALLINT,
    source     VARCHAR(12)  NOT NULL CHECK (source IN ('F1_ARCHIVE', 'RACE_CONTROL'))
);

CREATE INDEX idx_track_status_session ON track_status (session_id, starts_at);

-- Links to team radio clips (the audio stays on F1's servers).
CREATE TABLE team_radio (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id    BIGINT       NOT NULL REFERENCES sessions (id),
    driver_number SMALLINT     NOT NULL,
    recorded_at   TIMESTAMPTZ  NOT NULL,
    recording_url VARCHAR(500) NOT NULL
);

CREATE INDEX idx_team_radio_session ON team_radio (session_id, recorded_at);

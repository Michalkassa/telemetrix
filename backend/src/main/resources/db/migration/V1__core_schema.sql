-- V1 — Core reference data: seasons, circuits, drivers, constructors, races and their sessions.
--
-- Natural keys from the sources are UNIQUE so importers can upsert (re-running an import never
-- duplicates rows):
--   ref                 — Jolpica/Ergast ids ('monza', 'max_verstappen', 'red_bull')
--   openf1_*_key        — OpenF1 ids; only races from 2023 on have them, so they are nullable
-- Never edit this file once it has run; change the schema in a new V<n>__ file.

CREATE TABLE seasons (
    year SMALLINT PRIMARY KEY CHECK (year >= 1950) -- the first World Championship season
);

CREATE TABLE circuits (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ref                VARCHAR(50)  NOT NULL UNIQUE,
    name               VARCHAR(100) NOT NULL,
    locality           VARCHAR(100),
    country            VARCHAR(100),
    latitude           NUMERIC(9, 6),
    longitude          NUMERIC(9, 6),
    openf1_circuit_key INTEGER UNIQUE
);

CREATE TABLE drivers (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ref              VARCHAR(50)  NOT NULL UNIQUE,
    code             CHAR(3),            -- 'VER'; missing for many early drivers
    permanent_number SMALLINT,           -- only since 2014
    given_name       VARCHAR(100) NOT NULL,
    family_name      VARCHAR(100) NOT NULL,
    date_of_birth    DATE,
    nationality      VARCHAR(50)
);

CREATE TABLE constructors (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ref         VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    nationality VARCHAR(50)
);

-- One Grand Prix weekend (an OpenF1 "meeting").
CREATE TABLE races (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    season_year        SMALLINT     NOT NULL REFERENCES seasons (year),
    round              SMALLINT     NOT NULL CHECK (round > 0),
    circuit_id         BIGINT       NOT NULL REFERENCES circuits (id),
    name               VARCHAR(100) NOT NULL,
    race_date          DATE         NOT NULL,
    openf1_meeting_key INTEGER UNIQUE,
    UNIQUE (season_year, round)
);

CREATE INDEX idx_races_circuit_id ON races (circuit_id);

-- A session within a weekend: practice, qualifying, sprint, race.
CREATE TABLE sessions (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    race_id            BIGINT      NOT NULL REFERENCES races (id),
    openf1_session_key INTEGER UNIQUE,
    name               VARCHAR(50) NOT NULL, -- 'Practice 1', 'Qualifying', 'Sprint', 'Race', ...
    starts_at          TIMESTAMPTZ NOT NULL,
    ends_at            TIMESTAMPTZ,
    UNIQUE (race_id, name),
    CHECK (ends_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX idx_sessions_starts_at ON sessions (starts_at);

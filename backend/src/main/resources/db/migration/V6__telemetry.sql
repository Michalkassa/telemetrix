-- V6 — Full car telemetry (~3.7 Hz), downloaded only ON DEMAND: one car in one session, the first
-- time someone opens its telemetry view (docs/adr/0003). Replay doesn't need it (docs/adr/0004).

-- Which cars' telemetry has been downloaded: a later request makes no OpenF1 call.
CREATE TABLE telemetry_downloads (
    session_id    BIGINT      NOT NULL REFERENCES sessions (id),
    driver_number SMALLINT    NOT NULL,
    downloaded_at TIMESTAMPTZ NOT NULL,
    point_count   INTEGER     NOT NULL,
    dropped_count INTEGER     NOT NULL,   -- samples outside the session window or repeated
    PRIMARY KEY (session_id, driver_number)
);

-- One sample: car data, plus the nearest position within 0.5 s (null when there is none).
CREATE TABLE telemetry_points (
    session_id    BIGINT      NOT NULL REFERENCES sessions (id),
    driver_number SMALLINT    NOT NULL,
    recorded_at   TIMESTAMPTZ NOT NULL,
    speed         SMALLINT,   -- km/h
    throttle      SMALLINT,   -- %
    brake         SMALLINT,   -- 0 or 100
    gear          SMALLINT,
    rpm           INTEGER,
    drs           SMALLINT,   -- OpenF1's DRS code
    x             INTEGER,
    y             INTEGER,
    z             INTEGER,
    PRIMARY KEY (session_id, driver_number, recorded_at)
);

-- Stretches with no samples (> 2 s): recorded, never filled in.
CREATE TABLE telemetry_gaps (
    session_id    BIGINT      NOT NULL REFERENCES sessions (id),
    driver_number SMALLINT    NOT NULL,
    gap_from      TIMESTAMPTZ NOT NULL,
    gap_to        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, driver_number, gap_from)
);

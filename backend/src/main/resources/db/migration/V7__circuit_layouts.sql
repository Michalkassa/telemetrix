-- V7 — Track maps, per circuit and year (layouts change). The outline runs from the start/finish line
-- round one lap, in the raw coordinates OpenF1 positions use, so telemetry x/y sits on the same map.
-- Fetched once from Multiviewer, or built from one lap of OpenF1 positions (docs/adr/0003).

CREATE TABLE circuit_layouts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    circuit_id       BIGINT           NOT NULL REFERENCES circuits (id),
    year             SMALLINT         NOT NULL,
    source           VARCHAR(20)      NOT NULL,  -- MULTIVIEWER or OPENF1_LAP
    rotation         DOUBLE PRECISION NOT NULL DEFAULT 0, -- degrees counter-clockwise, to draw it upright
    x                INTEGER[]        NOT NULL,
    y                INTEGER[]        NOT NULL,
    sector1_end      DOUBLE PRECISION,           -- share of the lap distance where sector 1 ends
    sector2_end      DOUBLE PRECISION,
    sector_ends_from VARCHAR(20),                -- TRACK (mini-sectors) or ESTIMATED (sector times)
    corners          JSONB            NOT NULL DEFAULT '[]', -- [{number, x, y, progress}]
    fetched_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    UNIQUE (circuit_id, year)
);

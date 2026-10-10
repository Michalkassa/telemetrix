-- V12 — Championship standings after every round of a season (Step 30): one row per driver or
-- constructor per round, rebuilt race by race from the points awarded (StandingsCalculator).
CREATE TABLE championship_snapshots (
    season   SMALLINT         NOT NULL,
    round    SMALLINT         NOT NULL,
    kind     VARCHAR(12)      NOT NULL CHECK (kind IN ('DRIVER', 'CONSTRUCTOR')),
    ref      VARCHAR(50)      NOT NULL,   -- drivers.ref / constructors.ref
    position SMALLINT         NOT NULL,
    points   DOUBLE PRECISION NOT NULL,
    wins     SMALLINT         NOT NULL,
    PRIMARY KEY (season, round, kind, ref)
);

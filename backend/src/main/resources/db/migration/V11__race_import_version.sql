-- V11 — Which version of the race importer downloaded a race. Later versions download more (Step 21:
-- race control, weather, track status, team radio; Step 26: the track map's marshal sectors and pit
-- lane), so a race imported by an older one is imported again the next time someone opens it. Races
-- already imported count as version 0.
ALTER TABLE races ADD COLUMN import_version SMALLINT NOT NULL DEFAULT 0;

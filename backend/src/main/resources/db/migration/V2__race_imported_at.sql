-- V2 — Race data is downloaded per race, on demand (docs/adr/0003). imported_at records when a
-- race's data was fully downloaded; NULL means "not yet" (or the race hasn't happened yet).
ALTER TABLE races ADD COLUMN imported_at TIMESTAMPTZ;

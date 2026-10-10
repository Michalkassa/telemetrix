-- V13 — Driver analytics (Step 32): every driver's Grand Prix results in one flat, indexed table to
-- aggregate. A materialized view of the history (and, for opened races, OpenF1's weather): refreshed after
-- the history import and after each race import (DriverStatsService.refresh).
CREATE MATERIALIZED VIEW driver_race_results AS
SELECT e.id                AS race_entry_id,
       e.driver_id,
       e.race_id,
       e.constructor_id,
       e.car_number,
       r.season_year       AS season,
       r.round,
       r.circuit_id,
       sr.grid,
       sr.position,
       COALESCE(sr.classified, sr.position IS NOT NULL)                AS classified,
       sr.status,
       COALESCE(sr.points, 0)                                         AS points,
       sr.fastest_lap_rank,
       sr.status IS NOT NULL AND sr.status NOT IN ('DID_NOT_START', 'DID_NOT_QUALIFY') AS started,
       COALESCE(sr.status LIKE 'RETIRED%', false)                     AS dnf,
       -- places made up from the grid to the flag (grid 0: started from the pit lane)
       CASE WHEN COALESCE(sr.classified, true) AND sr.grid > 0 AND sr.position IS NOT NULL
            THEN sr.grid - sr.position END                             AS positions_gained,
       -- rain during the race (OpenF1 weather, 2023 on); null when unknown
       (SELECT bool_or(w.rainfall) FROM weather_data w JOIN sessions s ON s.id = w.session_id
        WHERE s.race_id = r.id AND s.name = 'Race')                   AS wet
FROM session_results sr
JOIN race_entries e ON e.id = sr.race_entry_id
JOIN races r ON r.id = e.race_id
WHERE sr.session_type = 'R';

CREATE UNIQUE INDEX idx_driver_race_results_entry ON driver_race_results (race_entry_id); -- refresh concurrently
CREATE INDEX idx_driver_race_results_driver ON driver_race_results (driver_id, season);
CREATE INDEX idx_driver_race_results_circuit ON driver_race_results (circuit_id);

-- V15 — Read models from the event store (Step 36, docs/adr/0006).

-- Every stream's latest generation: what the read models are worked out from.
CREATE VIEW current_events AS
SELECT e.*
FROM event_store e
WHERE e.generation = (SELECT max(x.generation) FROM event_store x
                      WHERE x.stream_type = e.stream_type AND x.stream_id = e.stream_id);

-- Driver analytics (Step 32) as a read model of the races' results streams: the same columns as V13's,
-- from each race's RESULT_RECORDED events (Grand Prix lines) and its RACE_RESULTS_RECORDED opening (wet).
DROP MATERIALIZED VIEW driver_race_results;

CREATE MATERIALIZED VIEW driver_race_results AS
WITH lines AS (
    SELECT ev.stream_id AS race_id, ev.payload AS p
    FROM current_events ev
    WHERE ev.stream_type = 'RACE_RESULTS' AND ev.type = 'RESULT_RECORDED' AND ev.payload ->> 'sessionType' = 'R'
), conditions AS (
    SELECT ev.stream_id AS race_id, (ev.payload ->> 'wet')::boolean AS wet
    FROM current_events ev
    WHERE ev.stream_type = 'RACE_RESULTS' AND ev.type = 'RACE_RESULTS_RECORDED'
), results AS (
    SELECT l.race_id,
           (l.p ->> 'raceEntryId')::bigint        AS race_entry_id,
           (l.p ->> 'driverId')::bigint           AS driver_id,
           (l.p ->> 'constructorId')::bigint      AS constructor_id,
           (l.p ->> 'carNumber')::smallint        AS car_number,
           (l.p ->> 'grid')::smallint             AS grid,
           (l.p ->> 'position')::smallint         AS position,
           (l.p ->> 'classified')::boolean        AS classified,
           (l.p ->> 'status')::varchar(30)        AS status,
           (l.p ->> 'points')::numeric(5, 2)      AS points,
           (l.p ->> 'fastestLapRank')::smallint   AS fastest_lap_rank
    FROM lines l
)
SELECT x.race_entry_id,
       x.driver_id,
       x.race_id,
       x.constructor_id,
       x.car_number,
       r.season_year       AS season,
       r.round,
       r.circuit_id,
       x.grid,
       x.position,
       COALESCE(x.classified, x.position IS NOT NULL)                AS classified,
       x.status,
       COALESCE(x.points, 0)                                         AS points,
       x.fastest_lap_rank,
       x.status IS NOT NULL AND x.status NOT IN ('DID_NOT_START', 'DID_NOT_QUALIFY') AS started,
       COALESCE(x.status LIKE 'RETIRED%', false)                     AS dnf,
       -- places made up from the grid to the flag (grid 0: started from the pit lane)
       CASE WHEN COALESCE(x.classified, true) AND x.grid > 0 AND x.position IS NOT NULL
            THEN x.grid - x.position END                             AS positions_gained,
       c.wet               -- rain during the race (OpenF1 weather, 2023 on); null when unknown
FROM results x
JOIN races r ON r.id = x.race_id
LEFT JOIN conditions c ON c.race_id = x.race_id;

CREATE UNIQUE INDEX idx_driver_race_results_entry ON driver_race_results (race_entry_id); -- refresh concurrently
CREATE INDEX idx_driver_race_results_driver ON driver_race_results (driver_id, season);
CREATE INDEX idx_driver_race_results_circuit ON driver_race_results (circuit_id);

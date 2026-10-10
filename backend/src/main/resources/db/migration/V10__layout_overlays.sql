-- V10 — More of the track map, for the replay's overlays (Step 26): where each marshalling sector starts
-- (yellow flags are given per marshalling sector) and the pit lane, traced from one car's positions
-- through a pit stop (raw OpenF1 coordinates, like the outline). Layouts imported before this have
-- neither: they show no yellow sectors or pit lane until imported again.
ALTER TABLE circuit_layouts
    ADD COLUMN marshal_sectors JSONB NOT NULL DEFAULT '[]', -- [{number, progress}]
    ADD COLUMN pit_lane_x INTEGER[],
    ADD COLUMN pit_lane_y INTEGER[];

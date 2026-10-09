-- V8 — Who drove which car number in a race or sprint session (OpenF1 /drivers): the replay's dot
-- labels and team colours.
CREATE TABLE session_drivers (
    session_id    BIGINT       NOT NULL REFERENCES sessions (id),
    driver_number SMALLINT     NOT NULL,
    code          VARCHAR(3),             -- 'VER'
    full_name     VARCHAR(100),
    team_name     VARCHAR(100),
    team_colour   VARCHAR(6),             -- hex without '#', e.g. '3671c6'
    headshot_url  VARCHAR(500),
    PRIMARY KEY (session_id, driver_number)
);

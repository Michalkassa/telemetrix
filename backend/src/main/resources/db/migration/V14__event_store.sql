-- V14 — The event store (Step 36, docs/adr/0006): every race session's and every race's results as an
-- append-only stream of events, the source of truth the read models are rebuilt from.
CREATE TABLE event_store (
    position    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- order across the whole store
    stream_type VARCHAR(20)  NOT NULL CHECK (stream_type IN ('SESSION', 'RACE_RESULTS')),
    stream_id   BIGINT       NOT NULL,   -- sessions.id / races.id
    generation  INTEGER      NOT NULL CHECK (generation > 0),
    seq         INTEGER      NOT NULL CHECK (seq > 0),
    type        VARCHAR(40)  NOT NULL,
    occurred_at TIMESTAMPTZ,
    payload     JSONB        NOT NULL,
    recorded_at TIMESTAMPTZ  NOT NULL,
    UNIQUE (stream_type, stream_id, generation, seq)
);

CREATE INDEX idx_event_store_occurred ON event_store (stream_type, stream_id, generation, occurred_at);

-- Events are facts: nothing changes or removes them. A stream worked out again is appended as its next
-- generation (readers take the latest), so earlier generations stay queryable.
CREATE FUNCTION event_store_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'event_store is append-only: % is not allowed', TG_OP;
END
$$;

CREATE TRIGGER event_store_no_change BEFORE UPDATE OR DELETE ON event_store
    FOR EACH ROW EXECUTE FUNCTION event_store_append_only();

CREATE TRIGGER event_store_no_truncate BEFORE TRUNCATE ON event_store
    FOR EACH STATEMENT EXECUTE FUNCTION event_store_append_only();

package com.telemetrix.eventstore;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// The event store (docs/adr/0006): append-only streams of events in event_store (the table rejects updates
// and deletes). Appending a stream again starts its next generation; reads see the latest one. A stream that
// has never been recorded is recorded on first read by its type's StreamRecorder (a race opened before
// there was a store). Every append is published (StreamAppended) for the read models to catch up.
@Repository
@Transactional
public class EventStore {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final JdbcTemplate jdbc;

	private final ObjectProvider<StreamRecorder> recorders;

	private final ApplicationEventPublisher publisher;

	private final Clock clock;

	EventStore(JdbcTemplate jdbc, ObjectProvider<StreamRecorder> recorders, ApplicationEventPublisher publisher,
			Clock clock) {
		this.jdbc = jdbc;
		this.recorders = recorders;
		this.publisher = publisher;
		this.clock = clock;
	}

	/** Appends `events`, in order, as the stream's next generation; returns that generation. */
	public int append(StreamId stream, List<NewEvent> events) {
		int generation = generation(stream) + 1;
		Timestamp now = Timestamp.from(clock.instant());
		List<Object[]> rows = new ArrayList<>();
		for (int i = 0; i < events.size(); i++) {
			NewEvent e = events.get(i);
			rows.add(new Object[] { stream.type().name(), stream.id(), generation, i + 1, e.type(),
					e.occurredAt() == null ? null : Timestamp.from(e.occurredAt()), JSON.writeValueAsString(e.payload()),
					now });
		}
		jdbc.batchUpdate("""
				insert into event_store (stream_type, stream_id, generation, seq, type, occurred_at, payload, recorded_at)
				values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)""", rows);
		publisher.publishEvent(new StreamAppended(stream, generation));
		return generation;
	}

	/** The stream's latest generation, recording the stream first if it has none. */
	public List<StoredEvent> read(StreamId stream) {
		int generation = generation(stream);
		if (generation == 0) {
			recorders.orderedStream().filter(r -> r.type() == stream.type()).findFirst()
					.ifPresent(r -> r.record(stream.id(), this));
			generation = generation(stream);
		}
		return generation == 0 ? List.of() : read(stream, generation);
	}

	/** One generation of a stream, in order. */
	@Transactional(readOnly = true)
	public List<StoredEvent> read(StreamId stream, int generation) {
		return query(stream, generation, Set.of(), null, null);
	}

	/** The stream's latest generation; 0 if it was never recorded. */
	@Transactional(readOnly = true)
	public int generation(StreamId stream) {
		Integer latest = jdbc.queryForObject(
				"select max(generation) from event_store where stream_type = ? and stream_id = ?", Integer.class,
				stream.type().name(), stream.id());
		return latest == null ? 0 : latest;
	}

	/**
	 * A generation's events (null: the latest), in order, keeping only `types` (empty: all) that happened
	 * from `from` up to and including `to` (either null: no limit; a bound leaves out events at no moment).
	 */
	@Transactional(readOnly = true)
	public List<StoredEvent> query(StreamId stream, Integer generation, Set<String> types, Instant from, Instant to) {
		int g = generation == null ? generation(stream) : generation;
		StringBuilder sql = new StringBuilder("""
				select position, generation, seq, type, occurred_at, payload::text as payload, recorded_at
				from event_store where stream_type = ? and stream_id = ? and generation = ?""");
		List<Object> args = new ArrayList<>(List.of(stream.type().name(), stream.id(), g));
		if (types != null && !types.isEmpty()) {
			sql.append(" and type = any (?)");
			args.add(types.toArray(String[]::new));
		}
		if (from != null) {
			sql.append(" and occurred_at >= ?");
			args.add(Timestamp.from(from));
		}
		if (to != null) {
			sql.append(" and occurred_at <= ?");
			args.add(Timestamp.from(to));
		}
		sql.append(" order by seq");
		return jdbc.query(sql.toString(), (rs, i) -> event(stream, rs), args.toArray());
	}

	private static StoredEvent event(StreamId stream, ResultSet rs) throws SQLException {
		Timestamp at = rs.getTimestamp("occurred_at");
		return new StoredEvent(rs.getLong("position"), stream, rs.getInt("generation"), rs.getInt("seq"),
				rs.getString("type"), at == null ? null : at.toInstant(), JSON.readTree(rs.getString("payload")),
				rs.getTimestamp("recorded_at").toInstant());
	}

}

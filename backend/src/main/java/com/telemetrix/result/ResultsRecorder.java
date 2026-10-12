package com.telemetrix.result;

import com.telemetrix.eventstore.EventStore;
import com.telemetrix.eventstore.NewEvent;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamRecorder;
import com.telemetrix.eventstore.StreamType;
import com.telemetrix.result.ResultsEvent.ResultRecorded;
import com.telemetrix.result.ResultsEvent.ResultsRecorded;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// The write side of race results (docs/adr/0006): each race's stream worked out from the imported history
// (session_results, Grand Prix and sprints) and its Race session's weather, appended as a new generation
// when it differs from the stored one. The standings and the driver analytics are read models of these
// streams. Works a season or the whole history at once in a few queries.
@Component
@Transactional
public class ResultsRecorder implements StreamRecorder {

	static final String OPENING = "RACE_RESULTS_RECORDED";

	static final String RESULT = "RESULT_RECORDED";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final JdbcTemplate jdbc;

	private final EventStore store;

	ResultsRecorder(JdbcTemplate jdbc, @Lazy EventStore store) {
		this.jdbc = jdbc;
		this.store = store;
	}

	@Override
	public StreamType type() {
		return StreamType.RACE_RESULTS;
	}

	@Override
	public void record(long raceId, EventStore store) {
		record(raceId);
	}

	/** Records one race if its results or conditions changed. */
	public void record(long raceId) {
		recordWhere("r.id = ?", raceId);
	}

	/** Records every race of a season that changed. */
	public void recordSeason(int season) {
		recordWhere("r.season_year = ?", season);
	}

	/** Records the races of a season that have no stream yet (imported before there was a store). */
	public void recordMissing(int season) {
		recordWhere("""
				r.season_year = ? and not exists (select 1 from event_store x
				where x.stream_type = 'RACE_RESULTS' and x.stream_id = r.id)""", season);
	}

	/** Records every race that changed (after the history import). */
	public void recordAll() {
		recordWhere("true");
	}

	private void recordWhere(String races, Object... args) {
		Map<Long, List<ResultsEvent>> derived = derive(races, args);
		Map<Long, List<ResultsEvent>> stored = stored(races, args);
		derived.forEach((raceId, events) -> {
			if (!events.equals(stored.get(raceId))) {
				store.append(new StreamId(StreamType.RACE_RESULTS, raceId), events.stream().map(ResultsRecorder::encode)
						.toList());
			}
		});
	}

	// each race's stream from the imported data: its conditions, then its lines in a stable order
	private Map<Long, List<ResultsEvent>> derive(String races, Object... args) {
		Map<Long, List<ResultsEvent>> streams = new LinkedHashMap<>();
		jdbc.query("""
				select r.id, (select bool_or(w.rainfall) from weather_data w join sessions s on s.id = w.session_id
				              where s.race_id = r.id and s.name = 'Race') as wet
				from races r where %s order by r.id""".formatted(races), rs -> {
			List<ResultsEvent> stream = new ArrayList<>();
			stream.add(new ResultsRecorded(rs.getObject("wet", Boolean.class)));
			streams.put(rs.getLong("id"), stream);
		}, args);
		jdbc.query("""
				select e.race_id, sr.session_type, sr.session_number, e.id as entry, d.id as driver, d.ref as driver_ref,
				       c.id as constructor, c.ref as constructor_ref, e.car_number, sr.grid, sr.position, sr.classified,
				       sr.status, sr.points, sr.fastest_lap_rank
				from session_results sr join race_entries e on e.id = sr.race_entry_id join races r on r.id = e.race_id
				join drivers d on d.id = e.driver_id join constructors c on c.id = e.constructor_id
				where sr.session_type in ('R', 'SR') and %s
				order by e.race_id, sr.session_type, sr.session_number, sr.position nulls last, e.id""".formatted(races),
				rs -> {
					var points = rs.getBigDecimal("points");
					streams.get(rs.getLong("race_id")).add(new ResultRecorded(rs.getString("session_type"),
							rs.getInt("session_number"), rs.getLong("entry"), rs.getLong("driver"),
							rs.getString("driver_ref"), rs.getLong("constructor"), rs.getString("constructor_ref"),
							rs.getObject("car_number", Integer.class), rs.getObject("grid", Integer.class),
							rs.getObject("position", Integer.class), rs.getObject("classified", Boolean.class),
							rs.getString("status"), points == null ? null : points.doubleValue(),
							rs.getObject("fastest_lap_rank", Integer.class)));
				}, args);
		return streams;
	}

	// the latest generation of each race's stream
	private Map<Long, List<ResultsEvent>> stored(String races, Object... args) {
		Map<Long, List<ResultsEvent>> streams = new LinkedHashMap<>();
		jdbc.query("""
				select ev.stream_id, ev.type, ev.payload::text as payload
				from current_events ev join races r on r.id = ev.stream_id
				where ev.stream_type = 'RACE_RESULTS' and %s order by ev.stream_id, ev.seq""".formatted(races), rs -> {
			streams.computeIfAbsent(rs.getLong("stream_id"), id -> new ArrayList<>())
					.add(decode(rs.getString("type"), rs.getString("payload")));
		}, args);
		return streams;
	}

	static NewEvent encode(ResultsEvent event) {
		return new NewEvent(event instanceof ResultsRecorded ? OPENING : RESULT, null, event);
	}

	private static ResultsEvent decode(String type, String payload) {
		Class<? extends ResultsEvent> as = type.equals(OPENING) ? ResultsRecorded.class : ResultRecorded.class;
		return JSON.readValue(payload, as);
	}

}

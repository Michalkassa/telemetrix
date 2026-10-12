package com.telemetrix.event;

import com.telemetrix.eventstore.EventStore;
import com.telemetrix.eventstore.Projection;
import com.telemetrix.eventstore.StoredEvent;
import com.telemetrix.eventstore.StreamAppended;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// A race session's timeline: a read model of its event stream (docs/adr/0006) — the events the detectors
// found, in stream (time) order. The write side (state.SessionRecorder) runs the detectors; this only reads.
// Kept in memory until the stream gets a new generation; rebuilt from the stream on demand.
@Service
public class TimelineService implements Projection {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	// the stream's opening (state.SessionEvent.SessionRecorded): the clock's zero and the drivers' codes
	private static final String OPENING = "SESSION_RECORDED";

	private final EventStore store;

	private final Map<Long, Timeline> timelines = new ConcurrentHashMap<>();

	TimelineService(EventStore store) {
		this.store = store;
	}

	public Timeline timeline(long sessionId) {
		return timelines.computeIfAbsent(sessionId, this::project);
	}

	@Override
	public String name() {
		return "timeline";
	}

	/** Drops every timeline, to be rebuilt from the streams when next asked for. */
	@Override
	public void rebuild() {
		timelines.clear();
	}

	// after the append commits (a rebuild before then would read the old generation)
	@TransactionalEventListener(fallbackExecution = true)
	public void onStreamAppended(StreamAppended appended) {
		if (appended.stream().type() == StreamType.SESSION) {
			timelines.remove(appended.stream().id());
		}
	}

	private Timeline project(long sessionId) {
		Instant clockStart = null;
		Map<Integer, String> codes = new LinkedHashMap<>();
		List<RaceEvent> events = new java.util.ArrayList<>();
		for (StoredEvent e : store.read(new StreamId(StreamType.SESSION, sessionId))) {
			if (e.type().equals(OPENING)) {
				clockStart = JSON.treeToValue(e.payload().get("clockStart"), Instant.class);
				for (Map.Entry<String, JsonNode> code : e.payload().get("codes").properties()) {
					codes.put(Integer.valueOf(code.getKey()), code.getValue().asString());
				}
			}
			else if (e.type().equals(RaceEvent.STORED_TYPE)) {
				events.add(JSON.treeToValue(e.payload(), RaceEvent.class));
			}
		}
		return new Timeline(sessionId, clockStart, Map.copyOf(codes), List.copyOf(events));
	}

}

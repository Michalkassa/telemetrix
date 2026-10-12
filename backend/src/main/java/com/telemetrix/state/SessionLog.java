package com.telemetrix.state;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.eventstore.EventStore;
import com.telemetrix.eventstore.StoredEvent;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// A race session's event log (docs/adr/0006) for looking into: its events, any generation, filtered by type
// and time (historical querying), and the race state right after any event (time travel debugging).
@Service
public class SessionLog {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** One generation's events (of `latestGeneration`); `clockStart`: the replay clock's zero. */
	public record Events(long sessionId, int generation, int latestGeneration, Instant clockStart,
			List<StoredEvent> events) {
	}

	/** The state right after event #`seq`. */
	public record EventState(int seq, String type, Instant at, RaceState state) {
	}

	private final EventStore store;

	private final RaceStateService states;

	SessionLog(EventStore store, RaceStateService states) {
		this.store = store;
		this.states = states;
	}

	/** `generation` null: the latest; `types` empty: all; `from`/`to` null: no limit. */
	public Events events(long sessionId, Integer generation, Set<String> types, Instant from, Instant to) {
		StreamId stream = new StreamId(StreamType.SESSION, sessionId);
		if (store.generation(stream) == 0) {
			store.read(stream); // a session opened before there was a store is recorded now (404 without timing)
		}
		int latest = store.generation(stream);
		int shown = generation == null ? latest : generation;
		if (shown < 1 || shown > latest) {
			throw new NotFoundException("Generation " + shown + " of the events of session", sessionId);
		}
		StoredEvent opening = store.query(stream, shown, Set.of("SESSION_RECORDED"), null, null).getFirst();
		return new Events(sessionId, shown, latest, JSON.treeToValue(opening.payload().get("clockStart"), Instant.class),
				store.query(stream, shown, types, from, to));
	}

	public EventState stateAtEvent(long sessionId, int seq) {
		List<SessionEvent> events = states.events(sessionId);
		if (seq < 1 || seq > events.size()) {
			throw new NotFoundException("Event #" + seq + " of session", sessionId);
		}
		RaceState state = states.stateAtEvent(sessionId, seq);
		return new EventState(seq, SessionEventCodec.encode(events.get(seq - 1)).type(), state.at(), state);
	}

}

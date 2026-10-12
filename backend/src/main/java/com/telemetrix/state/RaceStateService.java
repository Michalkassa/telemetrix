package com.telemetrix.state;

import com.telemetrix.event.RaceDataLoader;
import com.telemetrix.eventstore.EventStore;
import com.telemetrix.eventstore.Projection;
import com.telemetrix.eventstore.StreamAppended;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamType;
import com.telemetrix.ingestion.RaceImported;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;

// One RaceStateEngine per session: a fold over the session's stored event stream (docs/adr/0006) plus its
// timing for the cars' positions. Built on first use (taking its snapshots, a cache) and kept until the
// stream gets a new generation or the timing is imported again.
@Service
public class RaceStateService implements Projection {

	private final RaceDataLoader loader;

	private final EventStore store;

	private final Map<Long, RaceStateEngine> engines = new ConcurrentHashMap<>();

	RaceStateService(RaceDataLoader loader, EventStore store) {
		this.loader = loader;
		this.store = store;
	}

	public RaceState stateAt(long sessionId, Instant at) {
		return engine(sessionId).stateAt(at);
	}

	/** The session's stream, as folded. */
	public List<SessionEvent> events(long sessionId) {
		return engine(sessionId).events();
	}

	/** Time travel: the state right after event #`seq` of the session's stream. */
	public RaceState stateAtEvent(long sessionId, int seq) {
		return engine(sessionId).stateAtEvent(seq);
	}

	/**
	 * The snapshots the browser gets with a race: once per lap, at each key event, and at every change —
	 * so the latest one at or before any moment is the derived state then (not the 5 s ones).
	 */
	public List<Snapshot> shippedSnapshots(long sessionId) {
		return engine(sessionId).snapshots().stream()
				.filter(s -> s.reason() == SnapshotReason.LAP || s.reason() == SnapshotReason.CHANGE
						|| s.reason().isKeyEvent())
				.toList();
	}

	public Instant clockStart(long sessionId) {
		return engine(sessionId).clockStart();
	}

	@Override
	public String name() {
		return "race_state_snapshots";
	}

	/** Drops every engine and its snapshots, to be rebuilt from the streams when next asked for. */
	@Override
	public void rebuild() {
		engines.clear();
	}

	// the timing may have changed even when the stream didn't; before the push to viewers (TimelineUpdates)
	@TransactionalEventListener(fallbackExecution = true)
	@Order(0)
	public void onRaceImported(RaceImported imported) {
		imported.sessionIds().forEach(engines::remove);
	}

	// after the append commits (a rebuild before then would read the old generation)
	@TransactionalEventListener(fallbackExecution = true)
	public void onStreamAppended(StreamAppended appended) {
		if (appended.stream().type() == StreamType.SESSION) {
			engines.remove(appended.stream().id());
		}
	}

	private RaceStateEngine engine(long sessionId) {
		return engines.computeIfAbsent(sessionId, id -> RaceStateEngine.of(loader.load(id),
				SessionEventCodec.decode(store.read(new StreamId(StreamType.SESSION, id)))));
	}

}

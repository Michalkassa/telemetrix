package com.telemetrix.state;

import com.telemetrix.event.RaceDataLoader;
import com.telemetrix.ingestion.RaceImported;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;

// One RaceStateEngine per session, built on first use (loading the race and taking its snapshots) and
// kept until the race is imported again.
@Service
public class RaceStateService {

	private final RaceDataLoader loader;

	private final Map<Long, RaceStateEngine> engines = new ConcurrentHashMap<>();

	RaceStateService(RaceDataLoader loader) {
		this.loader = loader;
	}

	public RaceState stateAt(long sessionId, Instant at) {
		return engine(sessionId).stateAt(at);
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

	// after the import commits (a recompute before then would cache the old data); before the push to
	// viewers (TimelineUpdates)
	@TransactionalEventListener(fallbackExecution = true)
	@Order(0)
	public void onRaceImported(RaceImported imported) {
		imported.sessionIds().forEach(engines::remove);
	}

	private RaceStateEngine engine(long sessionId) {
		return engines.computeIfAbsent(sessionId, id -> RaceStateEngine.of(loader.load(id)));
	}

}

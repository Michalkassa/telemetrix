package com.telemetrix.state;

import com.telemetrix.event.RaceDataLoader;
import com.telemetrix.ingestion.RaceImported;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
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

	/** The snapshots the browser gets with a race: once per lap and at each key event. */
	public List<Snapshot> shippedSnapshots(long sessionId) {
		return engine(sessionId).snapshots().stream()
				.filter(s -> s.reason() == SnapshotReason.LAP || s.reason().isKeyEvent())
				.toList();
	}

	public Instant clockStart(long sessionId) {
		return engine(sessionId).clockStart();
	}

	@EventListener
	public void onRaceImported(RaceImported imported) {
		imported.sessionIds().forEach(engines::remove);
	}

	private RaceStateEngine engine(long sessionId) {
		return engines.computeIfAbsent(sessionId, id -> RaceStateEngine.of(loader.load(id)));
	}

}

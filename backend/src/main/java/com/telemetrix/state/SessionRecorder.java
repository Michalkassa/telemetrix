package com.telemetrix.state;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.event.EventDetector;
import com.telemetrix.event.RaceData;
import com.telemetrix.event.RaceDataLoader;
import com.telemetrix.event.RaceEvent;
import com.telemetrix.event.RaceEventDetected;
import com.telemetrix.eventstore.EventStore;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamRecorder;
import com.telemetrix.eventstore.StreamType;
import com.telemetrix.ingestion.RaceImported;
import com.telemetrix.state.SessionEvent.Detected;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionalEventListener;

// The write side of a race session (docs/adr/0006): works out its stream (SessionEvents) from the imported
// data and every detector's events, and appends it as a new generation when it differs from the stored one.
// Runs when a race's data is imported, and for a session first opened before there was a store. Each
// detected event is published (RaceEventDetected, the Observer pattern) when it's first recorded.
@Component
public class SessionRecorder implements StreamRecorder {

	private final RaceDataLoader loader;

	private final List<EventDetector> detectors;

	private final EventStore store;

	private final ApplicationEventPublisher publisher;

	private final TransactionTemplate ownTransaction;

	SessionRecorder(RaceDataLoader loader, List<EventDetector> detectors, @Lazy EventStore store,
			ApplicationEventPublisher publisher, PlatformTransactionManager transactions) {
		this.loader = loader;
		this.detectors = detectors;
		this.store = store;
		this.publisher = publisher;
		this.ownTransaction = new TransactionTemplate(transactions);
		this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Override
	public StreamType type() {
		return StreamType.SESSION;
	}

	/** Records the session's stream if it changed; a session without timing has none (404). */
	@Override
	public void record(long sessionId, EventStore store) {
		RaceData race = loader.load(sessionId);
		List<RaceEvent> detected = detectors.stream().flatMap(detector -> detector.detect(race).stream()).toList();
		List<SessionEvent> events = SessionEvents.derive(race, detected);
		StreamId stream = new StreamId(StreamType.SESSION, sessionId);
		int generation = store.generation(stream);
		if (generation > 0 && SessionEventCodec.decode(store.read(stream, generation)).equals(events)) {
			return;
		}
		store.append(stream, events.stream().map(SessionEventCodec::encode).toList());
		events.stream().filter(Detected.class::isInstance)
				.forEach(e -> publisher.publishEvent(new RaceEventDetected(sessionId, ((Detected) e).event())));
	}

	// after the import commits, each session in a transaction of its own (one without timing rolls back
	// only itself); before the read models drop what they had (Order 0) and viewers are told (TimelineUpdates)
	@TransactionalEventListener(fallbackExecution = true)
	@Order(-10)
	public void onRaceImported(RaceImported imported) {
		for (long sessionId : imported.sessionIds()) {
			try {
				ownTransaction.executeWithoutResult(status -> record(sessionId, store));
			}
			catch (NotFoundException noTiming) {
				// practice and qualifying: nothing to replay
			}
		}
	}

}

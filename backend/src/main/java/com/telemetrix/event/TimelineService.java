package com.telemetrix.event;

import com.telemetrix.ingestion.RaceImported;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

// A race session's timeline: every detector's events, in time order. Worked out once per session and
// kept until the race is imported again; each event is published (RaceEventDetected) when it's first
// detected — the Observer pattern.
@Service
public class TimelineService {

	private static final Comparator<RaceEvent> ORDER = Comparator.comparing(RaceEvent::at)
			.thenComparing(RaceEvent::type);

	private final List<EventDetector> detectors;

	private final RaceDataLoader loader;

	private final ApplicationEventPublisher publisher;

	private final Map<Long, Timeline> timelines = new ConcurrentHashMap<>();

	public TimelineService(List<EventDetector> detectors, RaceDataLoader loader, ApplicationEventPublisher publisher) {
		this.detectors = detectors;
		this.loader = loader;
		this.publisher = publisher;
	}

	public Timeline timeline(long sessionId) {
		return timelines.computeIfAbsent(sessionId, this::detect);
	}

	@EventListener
	public void onRaceImported(RaceImported imported) {
		imported.sessionIds().forEach(timelines::remove);
	}

	private Timeline detect(long sessionId) {
		RaceData race = loader.load(sessionId);
		List<RaceEvent> events = detectors.stream()
				.flatMap(detector -> detector.detect(race).stream())
				.sorted(ORDER)
				.toList();
		events.forEach(event -> publisher.publishEvent(new RaceEventDetected(sessionId, event)));
		return new Timeline(sessionId, race.clockStart(), Map.copyOf(race.codes()), events);
	}

}

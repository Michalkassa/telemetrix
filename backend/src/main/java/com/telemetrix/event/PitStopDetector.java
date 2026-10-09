package com.telemetrix.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

// Every pit stop, from pit entry to exit.
@Component
class PitStopDetector implements EventDetector {

	@Override
	public List<RaceEvent> detect(RaceData race) {
		List<RaceEvent> events = new ArrayList<>();
		race.pits().forEach((car, stops) -> stops.forEach(stop -> {
			int lap = stop.lap() != null ? stop.lap() : race.lapAt(stop.from());
			double seconds = Duration.between(stop.from(), stop.to()).toMillis() / 1000.0;
			events.add(new RaceEvent(EventType.PIT_STOP, stop.from(), lap, stop.to(), lap, Severity.LOW, List.of(car),
					String.format(Locale.ROOT, "Pit stop: %s (lap %d, %.1f s in the pit lane)", race.code(car), lap,
							seconds),
					null));
		}));
		return events;
	}

}

package com.telemetrix.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// Did not finish: a car whose timing stops before the chequered flag (a car laps down that takes the flag
// finished). From the replay's timelines, so it works without the official results.
@Component
class DnfDetector implements EventDetector {

	@Override
	public List<RaceEvent> detect(RaceData race) {
		List<RaceEvent> events = new ArrayList<>();
		race.timelines().forEach((car, timeline) -> {
			Instant stopped = race.retiredAt(car);
			if (stopped != null) {
				int lap = race.lapOf(timeline.finalLapsDone());
				events.add(new RaceEvent(EventType.DNF, stopped, lap, null, null, Severity.HIGH, List.of(car),
						"DNF: " + race.code(car) + " (lap " + lap + ")", null));
			}
		});
		return events;
	}

}

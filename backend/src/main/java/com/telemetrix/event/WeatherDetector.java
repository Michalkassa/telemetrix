package com.telemetrix.event;

import com.telemetrix.event.RaceData.Weather;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

// Weather changes: rain starting or stopping. The first reading is how the session began, not a change.
@Component
class WeatherDetector implements EventDetector {

	@Override
	public List<RaceEvent> detect(RaceData race) {
		List<RaceEvent> events = new ArrayList<>();
		Boolean raining = null;
		for (Weather reading : race.weather().stream().sorted(Comparator.comparing(Weather::at)).toList()) {
			if (raining != null && reading.rainfall() != raining) {
				events.add(new RaceEvent(EventType.WEATHER, reading.at(), race.lapAt(reading.at()), null, null,
						Severity.MEDIUM, List.of(), reading.rainfall() ? "Rain started" : "Rain stopped", null));
			}
			raining = reading.rainfall();
		}
		return events;
	}

}

package com.telemetrix.event;

import com.telemetrix.event.RaceData.LapTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

// Each new fastest lap of the race, at the moment it was completed. Lap 1 (a standing start) doesn't
// count.
@Component
class FastestLapDetector implements EventDetector {

	@Override
	public List<RaceEvent> detect(RaceData race) {
		List<RaceEvent> events = new ArrayList<>();
		int best = Integer.MAX_VALUE;
		List<LapTime> timed = race.laps().stream()
				.filter(l -> l.lap() > 1 && l.lapMs() != null && l.lapMs() > 0 && l.startsAt() != null)
				.sorted(Comparator.comparing(FastestLapDetector::endsAt))
				.toList();
		for (LapTime lap : timed) {
			if (lap.lapMs() < best) {
				best = lap.lapMs();
				events.add(new RaceEvent(EventType.FASTEST_LAP, endsAt(lap), lap.lap(), null, null, Severity.LOW,
						List.of(lap.car()), "Fastest lap: " + race.code(lap.car()) + " " + format(lap.lapMs()) + " (lap "
								+ lap.lap() + ")",
						null));
			}
		}
		return events;
	}

	private static Instant endsAt(LapTime lap) {
		return lap.startsAt().plusMillis(lap.lapMs());
	}

	// 94500 -> "1:34.500"
	static String format(int ms) {
		return String.format("%d:%02d.%03d", ms / 60_000, (ms / 1000) % 60, ms % 1000);
	}

}

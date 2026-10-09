package com.telemetrix.event;

import com.telemetrix.event.RaceData.LapTime;
import com.telemetrix.event.RaceData.Stint;
import com.telemetrix.replay.RaceReplay.PitWindow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// A new tyre stint is a tyre change: at the pit stop on the lap before it, or else when the stint's
// first lap started.
@Component
class TyreChangeDetector implements EventDetector {

	@Override
	public List<RaceEvent> detect(RaceData race) {
		List<RaceEvent> events = new ArrayList<>();
		var byCar = race.stints().stream().collect(Collectors.groupingBy(Stint::car));
		byCar.forEach((car, stints) -> {
			List<Stint> ordered = stints.stream().sorted(Comparator.comparingInt(Stint::stint)).toList();
			for (int i = 1; i < ordered.size(); i++) {
				Stint before = ordered.get(i - 1);
				Stint stint = ordered.get(i);
				int pitLap = stint.lapStart() - 1;
				Instant at = race.pits().getOrDefault(car, List.of()).stream()
						.filter(p -> Objects.equals(p.lap(), pitLap))
						.map(PitWindow::from)
						.findFirst()
						.orElseGet(() -> lapStart(race, car, stint.lapStart()));
				if (at == null) {
					continue;
				}
				events.add(new RaceEvent(EventType.TYRE_CHANGE, at, pitLap, null, null, Severity.LOW, List.of(car),
						race.code(car) + ": " + compound(before) + " → " + compound(stint), null));
			}
		});
		return events;
	}

	private static Instant lapStart(RaceData race, int car, int lap) {
		return race.laps().stream()
				.filter(l -> l.car() == car && l.lap() == lap)
				.map(LapTime::startsAt)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	private static String compound(Stint stint) {
		return stint.compound() == null ? "UNKNOWN" : stint.compound();
	}

}

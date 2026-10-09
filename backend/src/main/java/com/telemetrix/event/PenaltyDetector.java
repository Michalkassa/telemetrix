package com.telemetrix.event;

import com.telemetrix.event.RaceData.PenaltyIssued;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

// Every penalty the stewards handed out (Step 21 reads them from race control).
@Component
class PenaltyDetector implements EventDetector {

	@Override
	public List<RaceEvent> detect(RaceData race) {
		return race.penalties().stream()
				.map(p -> new RaceEvent(EventType.PENALTY, p.at(), p.lap() != null ? p.lap() : race.lapAt(p.at()), null,
						null, Severity.MEDIUM, List.of(p.car()), describe(p) + ": " + race.code(p.car())
								+ (p.reason() == null ? "" : " — " + p.reason().toLowerCase(Locale.ROOT)),
						null))
				.toList();
	}

	private static String describe(PenaltyIssued p) {
		return switch (p.kind()) {
			case TIME -> p.amount() + " s time penalty";
			case DRIVE_THROUGH -> "Drive-through penalty";
			case STOP_GO -> (p.amount() == null ? "" : p.amount() + " s ") + "stop-go penalty";
			case GRID -> p.amount() + "-place grid penalty";
		};
	}

}

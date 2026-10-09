package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.racecontrol.PenaltyKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class PenaltyDetectorTest {

	@Test
	void eachPenaltyIssued() {
		var race = new TestRace().car(14, "ALO", 0, 0, 1000, 10)
				.penalty(14, PenaltyKind.TIME, 5, "CROSSING THE LINE AT PIT ENTRY", 400, 23)
				.penalty(14, PenaltyKind.DRIVE_THROUGH, null, "SPEEDING IN THE PIT LANE", 500, 30)
				.build();

		assertThat(new PenaltyDetector().detect(race)).containsExactly(
				new RaceEvent(EventType.PENALTY, t(400), 23, null, null, Severity.MEDIUM, List.of(14),
						"5 s time penalty: ALO — crossing the line at pit entry", null),
				new RaceEvent(EventType.PENALTY, t(500), 30, null, null, Severity.MEDIUM, List.of(14),
						"Drive-through penalty: ALO — speeding in the pit lane", null));
	}

}

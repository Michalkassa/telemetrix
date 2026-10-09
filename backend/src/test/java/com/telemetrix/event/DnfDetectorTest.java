package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

// A car that stopped before the chequered flag did not finish; one that took the flag (even laps down) did.
class DnfDetectorTest {

	@Test
	void aCarThatStoppedBeforeTheFlagRetired() {
		var race = new TestRace().laps(5)
				.car(1, "VER", 0, 0, 500, 5) // the winner: the flag falls at 500 s
				.car(31, "OCO", 0, 0, 250, 2.4) // stopped part-way round lap 3
				.car(2, "SAR", 0, 0, 520, 4) // a lap down, but took the flag
				.build();

		var events = new DnfDetector().detect(race);

		assertThat(events).singleElement().isEqualTo(new RaceEvent(EventType.DNF, t(250), 3, null, null,
				Severity.HIGH, List.of(31), "DNF: OCO (lap 3)", null));
	}

}

package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PitStopDetectorTest {

	@Test
	void eachPitStopWithItsTimeInThePitLane() {
		var race = new TestRace().car(1, "VER", 0, 0, 1000, 10).pit(1, 17, 300, 324.1).build();

		assertThat(new PitStopDetector().detect(race)).containsExactly(new RaceEvent(EventType.PIT_STOP, t(300), 17,
				t(324.1), 17, Severity.LOW, List.of(1), "Pit stop: VER (lap 17, 24.1 s in the pit lane)", null));
	}

}

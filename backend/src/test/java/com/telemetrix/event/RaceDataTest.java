package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RaceDataTest {

	@Test
	void afterTheFlagCarsOnTheSameLapAreInTheOrderTheyFinished() {
		// both complete 10 laps; HAM (44) crosses the line 5 s before VER (1)
		var race = new TestRace().laps(10).car(1, "VER", 0, 0, 1005, 10).car(44, "HAM", 0, 0, 1000, 10).build();

		assertThat(race.runningOrder(t(2000))).containsExactly(44, 1);
		assertThat(race.positionAt(1, t(2000))).isEqualTo(2);
	}

}

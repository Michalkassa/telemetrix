package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

// Overtakes from the progress-based running order: one car going from behind another to ahead of it,
// and staying there. Laps are 100 s.
class OvertakeDetectorTest {

	@Test
	void aCarThatPassesAnotherOnTrack() {
		var race = new TestRace().laps(5)
				.car(1, "VER", 0, 0, 500, 5) // steady
				.car(44, "HAM", 0, 0, 100, 1.05, 300, 2.9, 500, 4.95) // ahead after lap 1, slower later
				.build();

		var events = new OvertakeDetector().detect(race);

		// VER (t/100) catches HAM (1.05 + (t-100) * 1.85/200) at t = 166.7 s: on lap 2
		assertThat(events).singleElement().satisfies(pass -> {
			assertThat(pass.type()).isEqualTo(EventType.OVERTAKE);
			assertThat(pass.participants()).isEqualTo(List.of(1, 44));
			assertThat(pass.at()).isBetween(t(166), t(168));
			assertThat(pass.lap()).isEqualTo(2);
			assertThat(pass.summary()).isEqualTo("VER overtakes HAM for P1");
			assertThat(pass.severity()).isEqualTo(Severity.MEDIUM);
		});
	}

	@Test
	void aPositionSwapWhileACarIsInThePitsIsNotAnOvertake() {
		var race = new TestRace().laps(5)
				.car(1, "VER", 0, 0, 500, 5)
				.car(44, "HAM", 0, 0, 100, 1.05, 200, 2.05, 230, 2.1, 500, 4.9) // pits on lap 3: VER goes by
				.pit(44, 3, 200, 225)
				.build();

		assertThat(new OvertakeDetector().detect(race)).isEmpty();
	}

	@Test
	void aSwapOnACarsPitLapBeforeItReachesThePitLaneIsNotAnOvertake() {
		// HAM's lap 3 is slow all the way round (the pit lane is in its last sector, timed as a whole), so
		// VER goes by at ~211 s, long before HAM enters the pit lane at 280 s
		var race = new TestRace().laps(5)
				.car(1, "VER", 0, 0, 500, 5)
				.car(44, "HAM", 0, 0, 100, 1.05, 200, 2.05, 300, 2.6, 500, 4.6)
				.pit(44, 3, 280, 300)
				.build();

		assertThat(new OvertakeDetector().detect(race)).isEmpty();
	}

	@Test
	void noOvertakesUnderTheSafetyCar() {
		var race = new TestRace().laps(5)
				.car(1, "VER", 0, 0, 500, 5)
				.car(44, "HAM", 0, 0, 100, 1.05, 300, 2.9, 500, 4.95) // the swap at 166.7 s, as above
				.status(com.telemetrix.racecontrol.TrackStatus.SAFETY_CAR, 120, 260.0)
				.build();

		assertThat(new OvertakeDetector().detect(race)).isEmpty();
	}

	@Test
	void passingACarThatHasStoppedIsNotAnOvertake() {
		var race = new TestRace().laps(5)
				.car(1, "VER", 0, 0, 500, 5)
				.car(44, "HAM", 0, 0, 100, 1.05, 150, 1.6) // stops at 150 s, VER passes the stopped car at 160 s
				.build();

		assertThat(new OvertakeDetector().detect(race)).isEmpty();
	}

	@Test
	void theStartIsNotCountedUntilTheLeaderHasDoneALap() {
		var race = new TestRace().laps(5)
				.car(1, "VER", 0, 0, 50, 0.6, 500, 5)
				.car(44, "HAM", 0, 0, 50, 0.4, 500, 4.9) // VER ahead of HAM from the first metres
				.build();

		assertThat(new OvertakeDetector().detect(race)).isEmpty();
	}

	@Test
	void aSwapUndoneWithinSecondsIsNotAnOvertake() {
		var race = new TestRace().laps(5)
				.car(1, "VER", 0, 0, 100, 1.1, 500, 5.0)
				// HAM gets ahead at 198.2 s and VER is back in front at 202.7 s: neither counts
				.car(44, "HAM", 0, 0, 100, 1.0, 195, 1.98, 200, 2.1, 202, 2.1, 205, 2.105, 500, 4.9)
				.build();

		assertThat(new OvertakeDetector().detect(race)).isEmpty();
	}

}

package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

// A new stint is a tyre change: at the pit stop before it, or else when its first lap started.
class TyreChangeDetectorTest {

	@Test
	void aNewCompoundAtThePitStopBeforeTheStint() {
		var race = new TestRace().car(1, "VER", 0, 0, 1000, 10)
				.stint(1, 1, "MEDIUM", 1).stint(1, 2, "HARD", 18)
				.pit(1, 17, 300, 324)
				.build();

		assertThat(new TyreChangeDetector().detect(race)).containsExactly(new RaceEvent(EventType.TYRE_CHANGE, t(300),
				17, null, null, Severity.LOW, List.of(1), "VER: MEDIUM → HARD", null));
	}

	@Test
	void withoutAPitStopTheStintsFirstLap() {
		var race = new TestRace().car(1, "VER", 0, 0, 1000, 10)
				.stint(1, 1, "SOFT", 1).stint(1, 2, "SOFT", 12)
				.lap(1, 12, 1100, 90_000)
				.build();

		assertThat(new TyreChangeDetector().detect(race)).singleElement().satisfies(e -> {
			assertThat(e.at()).isEqualTo(t(1100));
			assertThat(e.summary()).isEqualTo("VER: SOFT → SOFT");
		});
	}

	@Test
	void theFirstStintIsNotAChange() {
		var race = new TestRace().car(1, "VER", 0, 0, 1000, 10).stint(1, 1, "MEDIUM", 1).build();

		assertThat(new TyreChangeDetector().detect(race)).isEmpty();
	}

}

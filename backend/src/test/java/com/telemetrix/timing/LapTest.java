package com.telemetrix.timing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// The replay's timing points (docs/adr/0004): lap start, end of sector 1, end of sector 2, lap end.
class LapTest {

	static final Instant START = Instant.parse("2024-03-02T15:18:15.404Z");

	@Test
	void aCompleteLapHasFourTimingPoints() {
		var lap = new Lap(null, 1, 10, START);
		lap.setTimes(30_986, 42_009, 23_965, 96_960);

		assertThat(lap.isComplete()).isTrue();
		assertThat(lap.timingPoints()).containsExactly(START, START.plusMillis(30_986), START.plusMillis(72_995),
				START.plusMillis(96_960));
	}

	@Test
	void aLapMissingASectorHasOnlyItsStartAndEnd() {
		var lap = new Lap(null, 1, 1, START);
		lap.setTimes(null, 42_009, 23_965, 97_759);

		assertThat(lap.isComplete()).isFalse();
		assertThat(lap.timingPoints()).containsExactly(START, START.plusMillis(97_759));
	}

	@ParameterizedTest(name = "sector {0} missing")
	@ValueSource(ints = { 1, 2, 3 })
	void aLapMissingAnySectorIsPartial(int missing) {
		var lap = new Lap(null, 1, 5, START);
		lap.setTimes(missing == 1 ? null : 30_000, missing == 2 ? null : 40_000, missing == 3 ? null : 25_000, 95_000);

		assertThat(lap.isComplete()).isFalse();
		assertThat(lap.timingPoints()).containsExactly(START, START.plusMillis(95_000));
	}

	@Test
	void aLapWithoutAStartHasNoTimingPoints() {
		var lap = new Lap(null, 1, 1, null);
		lap.setTimes(30_000, 40_000, 25_000, 95_000);

		assertThat(lap.isComplete()).isFalse();
		assertThat(lap.timingPoints()).isEmpty();
	}

}

package com.telemetrix.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.telemetrix.telemetry.LapTrace.Sample;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// A lap of telemetry against distance, and two laps compared by where they are round the lap.
class LapTraceTest {

	static final Instant START = Instant.parse("2024-03-02T16:00:00Z");

	// samples every 250 ms at a steady speed, from a little before the lap to a little after
	static List<Sample> steady(int kmh, int lapMs) {
		List<Sample> samples = new ArrayList<>();
		for (int ms = -500; ms <= lapMs + 500; ms += 250) {
			samples.add(new Sample(START.plusMillis(ms), kmh, 100, 0, 7, 11_000, 0));
		}
		return samples;
	}

	@Test
	void distanceComesFromSpeedOverTime() {
		var trace = LapTrace.of(steady(180, 10_000), START, 10_000); // 50 m/s for 10 s

		assertThat(trace.distance()).isCloseTo(500, within(1e-6));
		assertThat(trace.points().getFirst().time()).isZero();
		assertThat(trace.points().getFirst().distance()).isZero();
		assertThat(trace.points().getLast().time()).isEqualTo(10_000);
		assertThat(trace.points().getLast().distance()).isCloseTo(500, within(1e-6));
		var halfway = trace.points().stream().filter(p -> p.time() == 5_000).findFirst().orElseThrow();
		assertThat(halfway.distance()).isCloseTo(250, within(1e-6));
		assertThat(halfway.speed()).isEqualTo(180);
	}

	@Test
	void onlyTheLapsSamplesBetweenItsStartAndEnd() {
		var trace = LapTrace.of(steady(180, 10_000), START, 10_000);

		assertThat(trace.points()).allSatisfy(p -> assertThat(p.time()).isBetween(0L, 10_000L));
	}

	@Test
	void theDeltaIsHowFarBehindTheOtherLapIsAtEachPointAndEndsAtTheLapTimeDifference() {
		var reference = LapTrace.of(steady(180, 10_000), START, 10_000);
		var slower = LapTrace.of(steady(180, 11_000), START, 11_000); // 10% slower everywhere

		var delta = LapTrace.delta(reference, slower);

		assertThat(delta.getFirst().delta()).isZero();
		assertThat(delta.getLast().delta()).isCloseTo(1.0, within(1e-9));
		assertThat(delta.getLast().distance()).isCloseTo(500, within(1e-6));
		var halfway = delta.stream().filter(d -> Math.abs(d.distance() - 250) < 1e-6).findFirst().orElseThrow();
		assertThat(halfway.delta()).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void sectorDeltasInSeconds() {
		assertThat(LapTrace.sectorDeltas(new Integer[] { 29_741, 39_916, 22_951 }, new Integer[] { 30_063, 40_650, 23_377 }))
				.containsExactly(0.322, 0.734, 0.426);
		assertThat(LapTrace.sectorDeltas(new Integer[] { 29_741, null, 22_951 }, new Integer[] { 30_063, 40_650, 23_377 }))
				.containsExactly(0.322, null, 0.426);
	}

}

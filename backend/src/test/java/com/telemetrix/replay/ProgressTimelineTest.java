package com.telemetrix.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.telemetrix.timing.Lap;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

// A car's progress round the race (docs/adr/0004): laps done as one number (9.25 = a quarter of the
// way round lap 10), interpolated in time between its timing points.
class ProgressTimelineTest {

	static final Instant START = Instant.parse("2024-03-02T15:00:00Z");

	// sector 1 ends a third of the way round, sector 2 at two thirds
	static final double S1 = 1.0 / 3;

	static final double S2 = 2.0 / 3;

	// lap 1: 30 + 30 + 30 s; lap 2: 31 + 30 + 29 s
	ProgressTimeline timeline = ProgressTimeline.fromLaps(
			List.of(lap(1, START, 30_000, 30_000, 30_000), lap(2, START.plusSeconds(90), 31_000, 30_000, 29_000)), S1, S2);

	@Test
	void eachCompleteLapGivesFourTimingPoints() {
		assertThat(timeline.points()).extracting(ProgressTimeline.TimingPoint::lapsDone)
				.containsExactly(0.0, S1, S2, 1.0, 1 + S1, 1 + S2, 2.0); // lap ends and next starts merged
	}

	@Test
	void atATimingPointItIsExactlyThere() {
		assertThat(timeline.lapsDoneAt(START.plusSeconds(30))).isCloseTo(S1, within(1e-9));
		assertThat(timeline.lapsDoneAt(START.plusSeconds(90))).isCloseTo(1.0, within(1e-9));
	}

	@Test
	void halfwayInTimeIsHalfwayInProgress() {
		assertThat(timeline.lapsDoneAt(START.plusSeconds(15))).isCloseTo(S1 / 2, within(1e-9));
		assertThat(timeline.lapsDoneAt(START.plusSeconds(90 + 15))).isCloseTo(1 + S1 * 15 / 31, within(1e-9));
	}

	@Test
	void beforeTheStartTheCarIsOnTheGrid() {
		assertThat(timeline.lapsDoneAt(START.minusSeconds(60))).isZero();
	}

	@Test
	void afterItsLastPointTheCarStaysThere() {
		assertThat(timeline.lapsDoneAt(START.plusSeconds(3600))).isEqualTo(2.0);
		assertThat(timeline.lastAt()).isEqualTo(START.plusSeconds(180));
	}

	@Test
	void aMissingSectorIsInterpolatedAcross() {
		var partial = lap(1, START, 30_000, 30_000, 30_000);
		partial.setTimes(null, 30_000, 30_000, 90_000);

		var t = ProgressTimeline.fromLaps(List.of(partial), S1, S2);

		assertThat(t.points()).hasSize(2);
		assertThat(t.lapsDoneAt(START.plusSeconds(30))).isCloseTo(1.0 / 3, within(1e-9)); // a third of the time
	}

	@Test
	void progressNeverRunsBackwards() {
		// a broken lap 3 that claims to start before lap 2 ended
		var t = ProgressTimeline.fromLaps(List.of(lap(1, START, 30_000, 30_000, 30_000),
				lap(2, START.plusSeconds(90), 30_000, 30_000, 30_000), lap(3, START.plusSeconds(100), 30_000, 30_000,
						30_000)), S1, S2);

		var samples = new double[200];
		for (int i = 0; i < samples.length; i++) {
			samples[i] = t.lapsDoneAt(START.plus(Duration.ofSeconds(i)));
		}
		for (int i = 1; i < samples.length; i++) {
			assertThat(samples[i]).isGreaterThanOrEqualTo(samples[i - 1]);
		}
	}

	@Test
	void findsWhenItReachedAPointOfTheRace() {
		assertThat(timeline.reachedAt(1.0)).isEqualTo(START.plusSeconds(90)); // completed lap 1
		assertThat(timeline.reachedAt(S1 / 2)).isEqualTo(START.plusSeconds(15));
		assertThat(timeline.reachedAt(5.0)).isNull(); // never got there
	}

	private static Lap lap(int number, Instant start, int s1, int s2, int s3) {
		var lap = new Lap(null, 1, number, start);
		lap.setTimes(s1, s2, s3, s1 + s2 + s3);
		return lap;
	}

}

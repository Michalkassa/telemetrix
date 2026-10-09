package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

// Each new fastest lap of the race, when it's completed. Lap 1 (a standing start) doesn't count.
class FastestLapDetectorTest {

	@Test
	void eachNewFastestLapInTheOrderTheyWereSet() {
		var race = new TestRace()
				.lap(1, 1, 0, 90_000) // lap 1: ignored
				.lap(1, 2, 90, 95_000) // 1:35.000, done at 185 s: the first fastest lap
				.lap(44, 2, 92, 96_000) // slower
				.lap(44, 3, 188, 94_500) // 1:34.500, done at 282.5 s
				.lap(1, 3, 185, 94_800) // slower than HAM's, though done earlier... at 279.8 s: still a new best then
				.lap(1, 4, 280, null) // no time
				.car(1, "VER", 0, 0, 400, 4).car(44, "HAM", 0, 0, 400, 4)
				.build();

		var events = new FastestLapDetector().detect(race);

		assertThat(events).extracting(RaceEvent::at, RaceEvent::participants, RaceEvent::lap).containsExactly(
				org.assertj.core.groups.Tuple.tuple(t(185), List.of(1), 2),
				org.assertj.core.groups.Tuple.tuple(t(279.8), List.of(1), 3),
				org.assertj.core.groups.Tuple.tuple(t(282.5), List.of(44), 3));
		assertThat(events.getLast().summary()).isEqualTo("Fastest lap: HAM 1:34.500 (lap 3)");
		assertThat(events).allSatisfy(e -> assertThat(e.severity()).isEqualTo(Severity.LOW));
	}

}

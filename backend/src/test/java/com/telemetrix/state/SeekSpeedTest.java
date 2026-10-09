package com.telemetrix.state;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.event.TestRace;
import java.util.Random;
import org.junit.jupiter.api.Test;

// Seeking anywhere in a full-size race (20 cars, 60 laps of 4 timing points, two stops each) stays far
// under 100 ms.
class SeekSpeedTest {

	@Test
	void seekingAnywhereIsQuick() {
		var race = new TestRace().laps(60);
		for (int car = 1; car <= 20; car++) {
			double lapTime = 90 + car * 0.2;
			double[] points = new double[2 * 241];
			for (int i = 0; i <= 240; i++) {
				points[2 * i] = i * lapTime / 4;
				points[2 * i + 1] = i / 4.0;
			}
			race.car(car, "C" + car, points).stint(car, 1, "MEDIUM", 1).stint(car, 2, "HARD", 21)
					.stint(car, 3, "SOFT", 41);
			race.pit(car, 20, 20 * lapTime - 20, 20 * lapTime + 4).pit(car, 40, 40 * lapTime - 20, 40 * lapTime + 4);
		}
		var engine = RaceStateEngine.of(race.build());
		var random = new Random(23);

		long slowest = 0;
		for (int i = 0; i < 500; i++) {
			long started = System.nanoTime();
			engine.stateAt(t(random.nextDouble() * 5600));
			slowest = Math.max(slowest, System.nanoTime() - started);
		}

		assertThat(slowest / 1_000_000).as("slowest seek, ms").isLessThan(100);
	}

}

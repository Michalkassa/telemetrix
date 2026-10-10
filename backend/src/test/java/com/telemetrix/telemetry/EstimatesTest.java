package com.telemetrix.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.telemetrix.telemetry.LapTrace.Sample;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// Fuel and ERS: not in any data source, so simple models, worked out by hand here — and always called
// estimates.
class EstimatesTest {

	static final Instant START = Instant.parse("2024-03-02T16:00:00Z");

	@Test
	void fuelStartsAtTheMaximumAndBurnsEvenlyOverTheRace() {
		assertThat(Estimates.fuelKg(1, 57)).isEqualTo(110.0); // full at the start of lap 1
		assertThat(Estimates.fuelKg(39, 57)).isCloseTo(110 * (1 - 38.0 / 57), within(1e-9)); // 36.67 kg
		assertThat(Estimates.fuelKg(58, 57)).isZero();
	}

	// `seconds` of samples every 100 ms with this throttle and brake
	static List<Sample> driving(double seconds, int throttle, int brake) {
		List<Sample> samples = new ArrayList<>();
		for (int ms = 0; ms <= seconds * 1000; ms += 100) {
			samples.add(new Sample(START.plusMillis(ms), 250, throttle, brake, 7, 11_000, 0));
		}
		return samples;
	}

	@Test
	void ersDeploysAt120KilowattsOnFullThrottleUpTo4MegajoulesALap() {
		assertThat(Estimates.ers(driving(10, 100, 0)).deployedMj()).isCloseTo(1.2, within(1e-9)); // 120 kW × 10 s
		assertThat(Estimates.ers(driving(60, 100, 0)).deployedMj()).isEqualTo(4.0);
		assertThat(Estimates.ers(driving(10, 60, 0)).deployedMj()).isZero(); // part throttle: no deployment
	}

	@Test
	void ersHarvestsUnderBrakingUpTo2MegajoulesALap() {
		assertThat(Estimates.ers(driving(5, 0, 100)).harvestedMj()).isCloseTo(0.6, within(1e-9));
		assertThat(Estimates.ers(driving(30, 0, 100)).harvestedMj()).isEqualTo(2.0);
	}

}

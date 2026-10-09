package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.ingestion.LapConsistencyValidator.Severity;
import com.telemetrix.ingestion.openf1.OpenF1Lap;
import com.telemetrix.ingestion.openf1.OpenF1PitStop;
import com.telemetrix.ingestion.openf1.OpenF1Stint;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LapConsistencyValidatorTest {

	LapConsistencyValidator validator = new LapConsistencyValidator();

	@Test
	void cleanLapsHaveNoFindings() {
		var laps = List.of(lap(1, 1, 30.0, 40.0, 25.0, 95.0), lap(1, 2, 30.1, 40.2, 25.3, 95.6));

		assertThat(validator.check(laps, List.of(), List.of())).isEmpty();
	}

	@Test
	void aGapInLapNumbersIsAnError() {
		var laps = List.of(lap(1, 1, 30.0, 40.0, 25.0, 95.0), lap(1, 3, 30.0, 40.0, 25.0, 95.0));

		assertThat(validator.check(laps, List.of(), List.of())).singleElement().satisfies(f -> {
			assertThat(f.severity()).isEqualTo(Severity.ERROR);
			assertThat(f.message()).contains("car 1").contains("lap 1").contains("lap 3");
		});
	}

	@Test
	void sectorsThatDoNotAddUpToTheLapAreAnError() {
		var laps = List.of(lap(16, 1, 30.0, 40.0, 25.0, 95.2)); // 95.0 vs 95.2

		assertThat(validator.check(laps, List.of(), List.of())).singleElement().satisfies(f -> {
			assertThat(f.severity()).isEqualTo(Severity.ERROR);
			assertThat(f.message()).contains("car 16 lap 1").contains("95.000").contains("95.200");
		});
	}

	@Test
	void sectorsWithinFiftyMillisecondsAreFine() {
		assertThat(validator.check(List.of(lap(16, 1, 30.0, 40.0, 25.0, 95.04)), List.of(), List.of())).isEmpty();
	}

	@Test
	void aMissingSectorIsNotAnError() {
		// lap 1 often has no sector 1 time: the lap is kept as partial
		assertThat(validator.check(List.of(lap(1, 1, null, 40.0, 25.0, 97.7)), List.of(), List.of())).isEmpty();
	}

	@Test
	void aPitStopWithoutAStintChangeIsAWarning() {
		var laps = List.of(lap(1, 1, 30.0, 40.0, 25.0, 95.0));
		var stints = List.of(new OpenF1Stint(1, 1, "SOFT", 1, 30, 3));
		var pits = List.of(new OpenF1PitStop(1, 17, OffsetDateTime.parse("2024-03-02T15:30:00Z"), 22.5, 2.4, null));

		assertThat(validator.check(laps, stints, pits)).singleElement().satisfies(f -> {
			assertThat(f.severity()).isEqualTo(Severity.WARNING);
			assertThat(f.message()).contains("car 1").contains("lap 17");
		});
	}

	@Test
	void aPitStopFollowedByANewStintIsFine() {
		var stints = List.of(new OpenF1Stint(1, 1, "SOFT", 1, 17, 3), new OpenF1Stint(1, 2, "HARD", 18, 57, 0));
		var pits = List.of(new OpenF1PitStop(1, 17, OffsetDateTime.parse("2024-03-02T15:30:00Z"), 22.5, 2.4, null));

		assertThat(validator.check(List.of(), stints, pits)).isEmpty();
	}

	private static OpenF1Lap lap(int car, int number, Double s1, Double s2, Double s3, Double lap) {
		return new OpenF1Lap(car, number, OffsetDateTime.parse("2024-03-02T15:00:00Z").plusSeconds(95L * number), s1,
				s2, s3, lap, null, null, null, false, null, null, null);
	}

}

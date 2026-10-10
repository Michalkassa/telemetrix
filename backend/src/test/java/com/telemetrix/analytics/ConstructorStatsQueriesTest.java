package com.telemetrix.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.telemetrix.RepositoryTest;
import com.telemetrix.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

// Constructor statistics in SQL against PostgreSQL, on a seeded mini-dataset (analytics/constructor-dataset.sql:
// 2024 rounds 1-2 and a 2023 race) where every number below was worked out by hand first.
@RepositoryTest
@Import(ConstructorStatsService.class)
@Sql("/analytics/constructor-dataset.sql")
class ConstructorStatsQueriesTest {

	static final double EPS = 1e-6;

	@Autowired
	ConstructorStatsService stats;

	@Test
	void reliabilityIsDnfsByCause() {
		var mercedes = stats.stats("mercedes", 2024).reliability();
		assertThat(mercedes.starts()).isEqualTo(4);
		assertThat(mercedes.finishes()).isEqualTo(2);
		assertThat(mercedes.dnfs()).isEqualTo(2);
		assertThat(mercedes.mechanical()).isEqualTo(1);
		assertThat(mercedes.incident()).isZero();
		assertThat(mercedes.unknown()).isEqualTo(1); // "Retired": the 2023-on data gives no cause
		assertThat(mercedes.causes()).extracting(c -> c.cause(), c -> c.count())
				.containsExactly(tuple("Engine", 1), tuple("Unknown", 1));

		// Pérez's gearbox failed with 90% run: classified, but not a finish
		assertThat(stats.stats("red_bull", 2024).reliability().finishes()).isEqualTo(3);

		var ferrari = stats.stats("ferrari", 2024).reliability();
		assertThat(ferrari.starts()).isEqualTo(3); // Sainz didn't start in Bahrain
		assertThat(ferrari.incident()).isEqualTo(1);
		assertThat(ferrari.causes()).extracting(c -> c.cause()).containsExactly("Accident");
	}

	@Test
	void pitStopMediansRankedInTheSeason() {
		var mercedes = stats.stats("mercedes", 2024).pitStops();
		assertThat(mercedes.stops()).isEqualTo(3); // not the red flag, nor 2023's stop
		assertThat(mercedes.medianLaneSeconds()).isEqualTo(23.0);
		assertThat(mercedes.rank()).isEqualTo(2);
		assertThat(mercedes.teams()).isEqualTo(3);
		assertThat(mercedes.fieldMedianLaneSeconds()).isEqualTo(23.0);
		assertThat(stats.stats("red_bull", 2024).pitStops().medianLaneSeconds()).isEqualTo(22.0);
		assertThat(stats.stats("ferrari", 2024).pitStops().rank()).isEqualTo(3);
	}

	@Test
	void racePaceIsTheMedianGapToTheFastestTeam() {
		assertThat(stats.stats("mercedes", 2024).pace().raceGapPercent()).isCloseTo(1.0735136, within(EPS));
		assertThat(stats.stats("ferrari", 2024).pace().raceGapPercent()).isCloseTo(3.3972192, within(EPS));
		assertThat(stats.stats("red_bull", 2024).pace().raceGapPercent()).isCloseTo(0.0, within(EPS));
	}

	@Test
	void qualifyingPaceIsTheBestLapOfAnySegment() {
		assertThat(stats.stats("mercedes", 2024).pace().qualifyingGapPercent()).isCloseTo(0.2572871, within(EPS));
		assertThat(stats.stats("red_bull", 2024).pace().qualifyingGapPercent()).isCloseTo(0.2173913, within(EPS));
		assertThat(stats.stats("ferrari", 2024).pace().qualifyingGapPercent()).isCloseTo(0.3932584, within(EPS));
	}

	@Test
	void developmentTrendRoundByRound() {
		var trend = stats.stats("mercedes", 2024).trend();
		assertThat(trend.rounds()).extracting(r -> r.round(), r -> r.race())
				.containsExactly(tuple(1, "Bahrain Grand Prix"), tuple(2, "Monaco Grand Prix"));
		assertThat(trend.rounds().get(0).qualifyingGapPercent()).isCloseTo(0.2247191, within(EPS));
		assertThat(trend.rounds().get(0).raceGapPercent()).isCloseTo(1.0869565, within(EPS));
		assertThat(trend.rounds().get(1).qualifyingGapPercent()).isCloseTo(0.2898551, within(EPS));
		assertThat(trend.rounds().get(1).raceGapPercent()).isCloseTo(1.0600707, within(EPS));
		assertThat(trend.qualifyingSlope()).isCloseTo(0.0651360, within(EPS));
		assertThat(trend.raceSlope()).isCloseTo(-0.0268858, within(EPS));
	}

	@Test
	void strategiesArePositionsGainedByNumberOfStops() {
		assertThat(stats.stats("mercedes", 2024).strategies())
				.extracting(s -> s.stops(), s -> s.finishes(), s -> s.averagePositionsGained())
				.containsExactly(tuple(1, 1, 1.0), tuple(2, 1, 2.0));
		assertThat(stats.stats("red_bull", 2024).strategies())
				.extracting(s -> s.stops(), s -> s.finishes(), s -> s.averagePositionsGained())
				.containsExactly(tuple(1, 3, 1.0));
		assertThat(stats.stats("ferrari", 2024).strategies())
				.extracting(s -> s.stops(), s -> s.finishes(), s -> s.averagePositionsGained())
				.containsExactly(tuple(1, 2, 0.5));
	}

	@Test
	void noStrategiesBeforeThereIsPitStopData() {
		assertThat(stats.stats("mercedes", 2010).strategies()).isEmpty();
	}

	@Test
	void withoutASeasonItIsTheTeamsLatest() {
		assertThat(stats.stats("mercedes", null).season()).isEqualTo(2024);
		assertThat(stats.stats("mercedes", 2023).reliability().dnfs()).isEqualTo(1);
	}

	@Test
	void anUnknownTeamOrASeasonItDidntRaceIsNotFound() {
		assertThatThrownBy(() -> stats.stats("nobody", 2024)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> stats.stats("williams", null)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> stats.stats("ferrari", 2023)).isInstanceOf(NotFoundException.class);
	}

}

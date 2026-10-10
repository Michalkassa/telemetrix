package com.telemetrix.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.telemetrix.RepositoryTest;
import com.telemetrix.common.BadRequestException;
import com.telemetrix.common.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

// The comparisons in SQL against PostgreSQL, on a seeded dataset (compare/compare-dataset.sql: Monza 2023 and
// 2024, Bahrain 2024) where every number below was worked out by hand first.
@RepositoryTest
@Import(CompareService.class)
@Sql("/compare/compare-dataset.sql")
class CompareServiceTest {

	@Autowired
	CompareService compare;

	@Test
	void theSameCircuitInTwoYears() {
		var monza = compare.circuit("monza", List.of(2023, 2024));

		assertThat(monza.circuit().name()).isEqualTo("Autodromo Nazionale di Monza");
		assertThat(monza.circuit().seasons()).containsExactly(2023, 2024); // every year it held a Grand Prix
		assertThat(monza.years()).extracting(y -> y.season(), y -> y.raceId(), y -> y.winner().ref(),
				y -> y.fastestLap().ms(), y -> y.fastestLap().code())
				.containsExactly(tuple(2023, 1L, "max_verstappen", 84000L, "VER"),
						tuple(2024, 2L, "leclerc", 83000L, "LEC"));
	}

	@Test
	void paceEvolutionIsTheFieldsMedianAndTheWinnersLapByLap() {
		var years = compare.circuit("monza", List.of(2023, 2024)).years();

		assertThat(years.get(0).pace()).extracting(p -> p.lap(), p -> p.medianMs(), p -> p.winnerMs())
				.containsExactly(tuple(1, 91000L, 90000L), tuple(2, 86000L, 85000L), tuple(3, 99000L, 84000L),
						tuple(4, 86000L, 86000L));
		assertThat(years.get(1).pace()).extracting(p -> p.lap(), p -> p.medianMs(), p -> p.winnerMs())
				.containsExactly(tuple(1, 91000L, 91000L), tuple(2, 85000L, 84000L), tuple(3, 88000L, 83000L),
						tuple(4, 86000L, 84000L));
	}

	@Test
	void strategiesWithCompoundsOnceTheRaceWasOpenedElseFromThePitStops() {
		var years = compare.circuit("monza", List.of(2023, 2024)).years();

		var history = years.get(0);
		assertThat(history.strategySource()).isEqualTo("PIT_STOPS");
		assertThat(history.strategies()).extracting(s -> s.code(), s -> s.stops()).containsExactly(tuple("VER", 0),
				tuple("SAI", 1), tuple("LEC", 1));
		assertThat(history.strategies().get(1).stints()).extracting(s -> s.compound(), s -> s.fromLap(), s -> s.toLap())
				.containsExactly(tuple(null, 1, 3), tuple(null, 4, 4));

		var opened = years.get(1);
		assertThat(opened.strategySource()).isEqualTo("TYRES");
		assertThat(opened.strategies()).extracting(s -> s.code(), s -> s.stops()).containsExactly(tuple("LEC", 0),
				tuple("VER", 1), tuple("SAI", 1));
		assertThat(opened.strategies().get(1).stints()).extracting(s -> s.compound(), s -> s.fromLap(), s -> s.toLap())
				.containsExactly(tuple("MEDIUM", 1, 3), tuple("HARD", 4, 4));
	}

	@Test
	void aCircuitMustHaveARaceInEveryYear() {
		assertThatThrownBy(() -> compare.circuit("monza", List.of(2023, 1990))).isInstanceOf(BadRequestException.class)
				.hasMessageContaining("1990");
		assertThatThrownBy(() -> compare.circuit("monza", List.of(2024))).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> compare.circuit("nowhere", List.of(2023, 2024))).isInstanceOf(NotFoundException.class);
	}

	@Test
	void theYearsACircuitHeldAGrandPrix() {
		assertThat(compare.circuitSeasons("monza")).containsExactly(2023, 2024);
		assertThatThrownBy(() -> compare.circuitSeasons("nowhere")).isInstanceOf(NotFoundException.class);
	}

	@Test
	void driverVersusDriverRaceByRace() {
		var h2h = compare.drivers("max_verstappen", "leclerc", 2024);

		assertThat(h2h.a().code()).isEqualTo("VER");
		assertThat(h2h.races()).extracting(r -> r.round(), r -> r.a().medianLapMs(), r -> r.b().medianLapMs(),
				r -> r.a().bestLapMs(), r -> r.b().bestLapMs(), r -> r.medianGapMs())
				.containsExactly(tuple(1, 95000L, 97000L, 95000L, 97000L, -2000L),
						tuple(16, 85000L, 84000L, 85000L, 83000L, 1000L));
		assertThat(h2h.races().get(0).b().statusDetail()).isEqualTo("Engine");
	}

	@Test
	void theHeadToHeadTally() {
		var tally = compare.drivers("max_verstappen", "leclerc", 2024).tally();

		assertThat(tally.races()).isEqualTo(2);
		assertThat(tally.aAheadInRace()).isEqualTo(1);
		assertThat(tally.bAheadInRace()).isEqualTo(1);
		assertThat(tally.aAheadOnGrid()).isEqualTo(2);
		assertThat(tally.bAheadOnGrid()).isZero();
		assertThat(tally.aPoints()).isEqualTo(43.0);
		assertThat(tally.bPoints()).isEqualTo(26.0);
	}

	@Test
	void driversMustExistAndDiffer() {
		assertThatThrownBy(() -> compare.drivers("max_verstappen", "nobody", 2024)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> compare.drivers("leclerc", "leclerc", 2024)).isInstanceOf(BadRequestException.class);
		assertThat(compare.drivers("max_verstappen", "leclerc", 2022).races()).isEmpty();
	}

}

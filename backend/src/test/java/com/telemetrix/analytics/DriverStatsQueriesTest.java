package com.telemetrix.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.telemetrix.RepositoryTest;
import com.telemetrix.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

// Driver statistics in SQL against PostgreSQL, on a seeded mini-dataset (analytics/mini-dataset.sql: 3
// races, 4 drivers) where every number below was worked out by hand first.
@RepositoryTest
@Import(DriverStatsService.class)
@Sql("/analytics/mini-dataset.sql")
class DriverStatsQueriesTest {

	@Autowired
	DriverStatsService stats;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void refresh() {
		stats.refresh(); // the materialized view, as after an import
	}

	@Test
	void careerTotals() {
		var hamilton = stats.stats("hamilton").career();
		assertThat(hamilton.starts()).isEqualTo(3);
		assertThat(hamilton.wins()).isEqualTo(2);
		assertThat(hamilton.podiums()).isEqualTo(3);
		assertThat(hamilton.dnfs()).isZero();
		assertThat(hamilton.poles()).isEqualTo(1);
		assertThat(hamilton.fastestLaps()).isEqualTo(1);
		assertThat(hamilton.averageFinish()).isCloseTo(4.0 / 3, within(1e-9));
		assertThat(hamilton.positionsGained()).isEqualTo(3);
		assertThat(hamilton.averagePositionsGained()).isEqualTo(1.0);
		assertThat(hamilton.points()).isEqualTo(69.0);
	}

	@Test
	void aNonStarterIsNotAStartAndARetirementIsADnf() {
		var leclerc = stats.stats("leclerc").career();
		assertThat(leclerc.starts()).isEqualTo(2);
		assertThat(leclerc.wins()).isZero();
		assertThat(leclerc.podiums()).isEqualTo(1);
		assertThat(leclerc.dnfs()).isEqualTo(1);
		assertThat(leclerc.averageFinish()).isEqualTo(3.0);
	}

	@Test
	void polesAreGridPositionOneAndPositionsCanBeLost() {
		var verstappen = stats.stats("max_verstappen").career();
		assertThat(verstappen.poles()).isEqualTo(2);
		assertThat(verstappen.fastestLaps()).isEqualTo(2);
		assertThat(verstappen.positionsGained()).isZero(); // +1, 0, −1
	}

	@Test
	void wetAgainstDry() {
		var wetDry = stats.stats("hamilton").wetDry();
		assertThat(wetDry.wet().races()).isEqualTo(1);
		assertThat(wetDry.wet().wins()).isEqualTo(1);
		assertThat(wetDry.wet().averageFinish()).isEqualTo(1.0);
		assertThat(wetDry.dry().races()).isEqualTo(2);
		assertThat(wetDry.dry().wins()).isEqualTo(1);
		assertThat(wetDry.dry().averageFinish()).isEqualTo(1.5);
	}

	@Test
	void eachTrack() {
		var tracks = stats.stats("hamilton").tracks();
		assertThat(tracks).extracting(t -> t.circuit(), t -> t.starts(), t -> t.wins(), t -> t.bestFinish())
				.containsExactly(org.assertj.core.groups.Tuple.tuple("bahrain", 2, 2, 1),
						org.assertj.core.groups.Tuple.tuple("monaco", 1, 0, 2));
	}

	@Test
	void pitStops() {
		var pits = stats.stats("hamilton").pitStops();
		assertThat(pits.stops()).isEqualTo(3);
		assertThat(pits.stopsPerRace()).isEqualTo(1.5);
		assertThat(pits.averageLaneSeconds()).isCloseTo(70.0 / 3, within(1e-9));
	}

	@Test
	void tyres() {
		var tyres = stats.stats("hamilton").tyres();
		assertThat(tyres).extracting(t -> t.compound(), t -> t.stints(), t -> t.laps())
				.containsExactly(org.assertj.core.groups.Tuple.tuple("HARD", 1, 37),
						org.assertj.core.groups.Tuple.tuple("SOFT", 1, 20));
		assertThat(tyres.getFirst().share()).isCloseTo(37.0 / 57, within(1e-9));
	}

	@Test
	void eachSeasonWithTheCareerSoFar() {
		var seasons = stats.stats("hamilton").seasons();
		assertThat(seasons).extracting(s -> s.season(), s -> s.wins(), s -> s.careerWins())
				.containsExactly(org.assertj.core.groups.Tuple.tuple(2024, 1, 1),
						org.assertj.core.groups.Tuple.tuple(2025, 1, 2));
	}

	@Test
	void theViewIsIndexedByDriver() {
		assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename = 'driver_race_results'",
				String.class)).anySatisfy(name -> assertThat(name).contains("driver"));
	}

	@Test
	void theDirectoryLatestSeasonFirstThenBySurname() {
		assertThat(stats.directory()).extracting(d -> d.ref(), d -> d.firstSeason(), d -> d.lastSeason(), d -> d.starts(),
				d -> d.wins())
				.containsExactly(tuple("bottas", 2024, 2025, 3, 0), tuple("hamilton", 2024, 2025, 3, 2),
						tuple("leclerc", 2024, 2025, 2, 0), tuple("max_verstappen", 2024, 2025, 3, 1));
	}

	@Test
	void theRaceLogNewestFirstWithWhyItEnded() {
		var log = stats.raceLog("leclerc");
		assertThat(log).extracting(r -> r.season(), r -> r.round(), r -> r.raceId(), r -> r.team(), r -> r.grid(),
				r -> r.position(), r -> r.status(), r -> r.statusDetail(), r -> r.points())
				.containsExactly(
						tuple(2025, 1, 3L, "Ferrari", 0, 4, "DID_NOT_START", "Did not start", 0.0),
						tuple(2024, 2, 2L, "Ferrari", 2, 4, "RETIRED_INCIDENT", "Accident", 0.0),
						tuple(2024, 1, 1L, "Ferrari", 2, 3, "FINISHED", "Finished", 15.0));
		assertThat(log.getFirst().race()).isEqualTo("Bahrain Grand Prix");
		assertThat(log.getFirst().classified()).isFalse();
	}

	@Test
	void anUnknownDriversRaceLogIsNotFound() {
		assertThatThrownBy(() -> stats.raceLog("nobody")).isInstanceOf(NotFoundException.class);
	}

}

package com.telemetrix.championship;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.RepositoryTest;
import com.telemetrix.eventstore.EventStore;
import com.telemetrix.result.ResultsRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

// The standings are a read model of the races' results streams (docs/adr/0006): built from the events
// alone, rebuilt when a race's stream gets a new generation. On the mini-dataset: 2024 is two rounds —
// HAM 26 + 18, VER 18 + 26, LEC 15 + 0, BOT 0 + 15.
@RepositoryTest
@Import({ ChampionshipService.class, ResultsRecorder.class, EventStore.class, ChampionshipProjectionTest.Beans.class })
@Sql("/analytics/mini-dataset.sql")
class ChampionshipProjectionTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class Beans {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-10-12T20:00:00Z"), ZoneOffset.UTC);
		}

	}

	@Autowired
	ChampionshipService championship;

	@Autowired
	ResultsRecorder results;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void builtFromTheResultsStreams() {
		assertThat(points(2024)).isEqualTo(Map.of("hamilton", 44.0, "max_verstappen", 44.0, "leclerc", 15.0,
				"bottas", 15.0));
		assertThat(championship.standings(2024, null).rounds()).isEqualTo(2);
	}

	@Test
	void rebuiltFromTheEventsAloneWithoutTheImportedResults() {
		var before = championship.standings(2024, null);
		jdbc.update("delete from session_results");

		championship.rebuild();

		assertThat(championship.standings(2024, null)).isEqualTo(before);
	}

	@Test
	void aRaceRecordedAgainWithDifferentResultsRebuildsItsSeason() {
		championship.standings(2024, null);
		jdbc.update("update session_results set points = 30 where race_entry_id = 11 and session_type = 'R'");

		results.record(1);

		assertThat(points(2024)).containsEntry("hamilton", 48.0);
	}

	private Map<String, Double> points(int season) {
		return championship.standings(season, null).drivers().stream()
				.collect(Collectors.toMap(ChampionshipStandings.Driver::ref, ChampionshipStandings.Driver::points));
	}

}

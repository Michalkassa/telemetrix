package com.telemetrix.championship;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.ingestion.history.HistoryImportService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

// Outer test for the Seasons lens' data: a season's race-by-race results grid and every driver's points
// progression, from the recorded Jolpica dump (its 2008 season: the Australian GP).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class SeasonPagesApiFlowTest {

	@Autowired
	JobOperator jobOperator;

	@Autowired
	Job jolpicaHistoryImport;

	@Autowired
	RestTestClient http;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void importHistory() throws Exception {
		jdbc.execute("""
				truncate seasons, circuits, drivers, constructors, races, sessions, race_entries, session_results,
				         lap_times, pit_stops, championship_snapshots restart identity cascade""");
		var dump = new ClassPathResource("fixtures/jolpica/dump").getFile().toPath();
		jobOperator.start(jolpicaHistoryImport, HistoryImportService.parameters(dump, System.nanoTime()));
	}

	@Test
	@SuppressWarnings("unchecked")
	void theResultsGridHasACellPerDriverPerRaceLinkingToTheRace() {
		long race = jdbc.queryForObject("select id from races where season_year = 2008", Long.class);

		Map<String, Object> grid = get("/api/seasons/2008/results-grid");

		var races = (List<Map<String, Object>>) grid.get("races");
		assertThat(races).singleElement().satisfies(r -> {
			assertThat(r).containsEntry("round", 1).containsEntry("raceId", (int) race)
					.containsEntry("name", "Australian Grand Prix");
		});
		var drivers = (List<Map<String, Object>>) grid.get("drivers");
		assertThat(drivers).hasSize(22); // in championship order
		var hamilton = drivers.getFirst();
		assertThat(hamilton).containsEntry("code", "HAM").containsEntry("position", 1).containsEntry("points", 10.0);
		assertThat((List<Map<String, Object>>) hamilton.get("cells")).singleElement().satisfies(c -> {
			assertThat(c).containsEntry("round", 1).containsEntry("raceId", (int) race).containsEntry("position", 1)
					.containsEntry("points", 10.0).containsEntry("status", "FINISHED");
		});
		var barrichello = drivers.stream().filter(d -> "BAR".equals(d.get("code"))).findFirst().orElseThrow();
		assertThat((List<Map<String, Object>>) barrichello.get("cells")).singleElement()
				.satisfies(c -> assertThat(c).containsEntry("status", "DISQUALIFIED").containsEntry("position", null));
	}

	@Test
	@SuppressWarnings("unchecked")
	void everyDriversPointsRoundByRound() {
		Map<String, Object> progression = get("/api/seasons/2008/progression");

		var drivers = (List<Map<String, Object>>) progression.get("drivers");
		var hamilton = drivers.getFirst();
		assertThat(hamilton).containsEntry("ref", "hamilton").containsEntry("code", "HAM");
		assertThat((List<Map<String, Object>>) hamilton.get("rounds")).containsExactly(Map.of("round", 1, "points", 10.0));
	}

	@Test
	void anUnknownSeasonIsNotFound() {
		http.get().uri("/api/seasons/1949/results-grid").exchange().expectStatus().isNotFound();
		http.get().uri("/api/seasons/1949/progression").exchange().expectStatus().isNotFound();
	}

	private Map<String, Object> get(String path) {
		return http.get().uri(path).exchange().expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				}).returnResult().getResponseBody();
	}

}

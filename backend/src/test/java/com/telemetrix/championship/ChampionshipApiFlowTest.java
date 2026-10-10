package com.telemetrix.championship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.Fixtures;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.ingestion.RaceImporter;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.history.HistoryImportService;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

// Outer test: championship standings over HTTP, from the recorded Jolpica dump (Bahrain 2024 is round 1),
// and the standings "if they finished now" during the replay of that race (recorded OpenF1 data). Every
// expected value comes from the API's own results.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ChampionshipApiFlowTest {

	@Autowired
	JobOperator jobOperator;

	@Autowired
	Job jolpicaHistoryImport;

	@Autowired
	RaceImporter importer;

	@Autowired
	RestTestClient http;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	OpenF1Client openF1;

	@MockitoBean
	MultiviewerClient multiviewer;

	@MockitoBean
	F1ArchiveClient f1Archive;

	long bahrain;

	@BeforeEach
	void importHistoryAndOpenBahrain2024() throws Exception {
		jdbc.execute("""
				truncate seasons, circuits, drivers, constructors, races, sessions, race_entries, session_results,
				         lap_times, pit_stops, championship_snapshots restart identity cascade""");
		var dump = new ClassPathResource("fixtures/jolpica/dump").getFile().toPath();
		jobOperator.start(jolpicaHistoryImport, HistoryImportService.parameters(dump, System.nanoTime()));
		bahrain = jdbc.queryForObject("select id from races where season_year = 2024", Long.class);
		jdbc.update("update races set openf1_meeting_key = 1229 where id = ?", bahrain);
		given(openF1.sessions(1229)).willReturn(Fixtures.openF1Sessions(1229));
		given(openF1.laps(9472)).willReturn(Fixtures.openF1Laps(9472));
		given(openF1.drivers(9472)).willReturn(Fixtures.openF1Drivers(9472));
		given(openF1.stints(9472)).willReturn(Fixtures.openF1Stints(9472));
		given(openF1.pitStops(9472)).willReturn(Fixtures.openF1PitStops(9472));
		importer.importRace(bahrain);
	}

	@SuppressWarnings("unchecked")
	Map<String, Double> awarded() {
		var results = (List<Map<String, Object>>) get("/api/races/" + bahrain + "/results").get("results");
		return results.stream().collect(Collectors.toMap(r -> (String) r.get("surname"),
				r -> ((Number) r.get("points")).doubleValue()));
	}

	@Test
	@SuppressWarnings("unchecked")
	void theStandingsAfterRoundOneAreThePointsItAwarded() {
		Map<String, Object> standings = get("/api/seasons/2024/standings");

		assertThat(standings).containsEntry("season", 2024).containsEntry("round", 1);
		var drivers = (List<Map<String, Object>>) standings.get("drivers");
		var awarded = awarded();
		assertThat(drivers).hasSize(awarded.size());
		for (var d : drivers) {
			assertThat(((Number) d.get("points")).doubleValue()).as((String) d.get("surname"))
					.isEqualTo(awarded.get((String) d.get("surname")));
		}
		assertThat(drivers.getFirst()).containsEntry("position", 1).containsEntry("wins", 1);
		assertThat(jdbc.queryForObject("select count(*) from championship_snapshots where season = 2024", Long.class))
				.isPositive();
	}

	@Test
	@SuppressWarnings("unchecked")
	void ifTheyFinishedNowAtTheFlagIsWhatTheyWereAwarded() {
		String chequered = (String) get("/api/sessions/" + session() + "/replay").get("chequeredAt");

		Map<String, Object> live = get("/api/sessions/" + session() + "/replay/championship?at=" + chequered);

		var awarded = awarded();
		var drivers = (List<Map<String, Object>>) live.get("drivers");
		assertThat(drivers).isNotEmpty();
		for (var d : drivers) {
			assertThat(((Number) d.get("total")).doubleValue()).as((String) d.get("surname"))
					.isEqualTo(awarded.get((String) d.get("surname")));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void theRoundsSprintCountsBeforeItsGrandPrix() {
		// a sprint the day before (the recorded dump has none for Bahrain 2024): Verstappen won it, 8 points
		jdbc.update("""
				insert into session_results (race_entry_id, session_type, session_number, position, points, classified)
				select e.id, 'SR', 1, 1, 8, true from race_entries e join drivers d on d.id = e.driver_id
				where e.race_id = ? and d.ref = 'max_verstappen'""", bahrain);
		String chequered = (String) get("/api/sessions/" + session() + "/replay").get("chequeredAt");

		var drivers = (List<Map<String, Object>>) get("/api/sessions/" + session() + "/replay/championship?at=" + chequered)
				.get("drivers");

		var max = drivers.stream().filter(d -> d.get("ref").equals("max_verstappen")).findFirst().orElseThrow();
		assertThat(((Number) max.get("before")).doubleValue()).isEqualTo(8.0);
		assertThat(((Number) max.get("total")).doubleValue()).isEqualTo(8.0 + awarded().get("Verstappen"));
	}

	@Test
	void anUnknownSeasonIsNotFound() {
		http.get().uri("/api/seasons/1949/standings").exchange().expectStatus().isNotFound();
	}

	long session() {
		return jdbc.queryForObject("select id from sessions where openf1_session_key = 9472", Long.class);
	}

	private Map<String, Object> get(String path) {
		return http.get().uri(path).exchange().expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				}).returnResult().getResponseBody();
	}

}

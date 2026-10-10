package com.telemetrix.result;

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

// Outer test for the race hub's leaves: results and lap pace over HTTP, from the recorded Jolpica dump
// (British GP 1950, Australian GP 2008, Bahrain GP 2024) and, for 2024, recorded OpenF1 laps. Every
// expected value is read from those recorded files.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class RaceHubApiFlowTest {

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

	@BeforeEach
	void importHistory() throws Exception {
		jdbc.execute("""
				truncate seasons, circuits, drivers, constructors, races, sessions, race_entries, session_results,
				         lap_times, pit_stops restart identity cascade""");
		var dump = new ClassPathResource("fixtures/jolpica/dump").getFile().toPath();
		jobOperator.start(jolpicaHistoryImport, HistoryImportService.parameters(dump, System.nanoTime()));
	}

	long race(int year) {
		return jdbc.queryForObject("select id from races where season_year = ?", Long.class, year);
	}

	@Test
	@SuppressWarnings("unchecked")
	void theRaceResultInFinishingOrder() {
		Map<String, Object> body = get("/api/races/" + race(2008) + "/results");

		List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
		assertThat(results).hasSize(22);
		assertThat(results.getFirst()).containsEntry("position", 1).containsEntry("code", "HAM")
				.containsEntry("surname", "Hamilton").containsEntry("team", "McLaren").containsEntry("number", 22)
				.containsEntry("grid", 1).containsEntry("laps", 58).containsEntry("timeMs", 5_690_616)
				.containsEntry("status", "FINISHED").containsEntry("points", 10.0);
		assertThat(results.get(1)).containsEntry("code", "HEI").containsEntry("grid", 5);
		assertThat(results.getLast()).containsEntry("code", "BAR").containsEntry("status", "DISQUALIFIED")
				.containsEntry("statusDetail", "Disqualified");
	}

	@Test
	@SuppressWarnings("unchecked")
	void a1950ResultWithoutDriverCodes() {
		List<Map<String, Object>> results = (List<Map<String, Object>>) get("/api/races/" + race(1950) + "/results")
				.get("results");

		assertThat(results).hasSize(23);
		assertThat(results.getFirst()).containsEntry("surname", "Farina").containsEntry("team", "Alfa Romeo")
				.containsEntry("code", null);
	}

	@Test
	@SuppressWarnings("unchecked")
	void lapTimesFromTheHistoryBefore2023() {
		Map<String, Object> body = get("/api/races/" + race(2008) + "/lap-times");

		assertThat(body).containsEntry("source", "JOLPICA");
		List<Map<String, Object>> drivers = (List<Map<String, Object>>) body.get("drivers");
		assertThat(drivers).hasSize(17);
		var hamilton = drivers.getFirst(); // in finishing order
		assertThat(hamilton).containsEntry("code", "HAM").containsEntry("position", 1);
		assertThat((List<Map<String, Object>>) hamilton.get("laps")).containsExactly(
				Map.of("lap", 1, "ms", 121_465), Map.of("lap", 2, "ms", 152_471), Map.of("lap", 3, "ms", 89_257),
				Map.of("lap", 39, "ms", 87_452));
	}

	@Test
	void noLapTimesBefore1996() {
		assertThat(get("/api/races/" + race(1950) + "/lap-times")).containsEntry("source", "NONE")
				.containsEntry("drivers", List.of());
	}

	@Test
	@SuppressWarnings("unchecked")
	void lapTimesFromOpenF1OnceTheRaceIsOpened() {
		long bahrain = race(2024);
		jdbc.update("update races set openf1_meeting_key = 1229 where id = ?", bahrain);
		given(openF1.sessions(1229)).willReturn(Fixtures.openF1Sessions(1229));
		given(openF1.laps(9472)).willReturn(Fixtures.openF1Laps(9472));
		given(openF1.drivers(9472)).willReturn(Fixtures.openF1Drivers(9472));
		importer.importRace(bahrain);

		Map<String, Object> body = get("/api/races/" + bahrain + "/lap-times");

		assertThat(body).containsEntry("source", "OPENF1");
		List<Map<String, Object>> drivers = (List<Map<String, Object>>) body.get("drivers");
		assertThat(drivers).hasSize(20);
		var verstappen = drivers.getFirst();
		assertThat(verstappen).containsEntry("code", "VER").containsEntry("number", 1).containsEntry("position", 1);
		var laps = (List<Map<String, Object>>) verstappen.get("laps");
		assertThat(laps).hasSize(57);
	}

	@Test
	void anUnknownRaceIsNotFound() {
		http.get().uri("/api/races/999999/results").exchange().expectStatus().isNotFound();
		http.get().uri("/api/races/999999/lap-times").exchange().expectStatus().isNotFound();
	}

	private Map<String, Object> get(String path) {
		return http.get().uri(path).exchange().expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				}).returnResult().getResponseBody();
	}

}

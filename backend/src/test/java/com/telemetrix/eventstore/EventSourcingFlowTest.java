package com.telemetrix.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.Fixtures;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.ingestion.RaceImporter;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.history.HistoryImportService;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

// Outer test for Step 36 (the Check-it): the event streams are the source of truth. With the recorded
// history (British GP 1950, Australian GP 2008, Bahrain GP 2024) and the 2023 Singapore GP opened, every
// read model — timeline, race state and its snapshots, standings, the analytics view — is thrown away and
// rebuilt from the events alone (the imported data they came from deleted first): the answers don't change.
// Plus the event log itself: querying it, and time travel to any event.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class EventSourcingFlowTest {

	@Autowired
	RaceImporter importer;

	@Autowired
	JobOperator jobOperator;

	@Autowired
	Job jolpicaHistoryImport;

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

	long raceId;

	long sessionId;

	@BeforeEach
	void importHistoryAndOpenSingapore2023() throws Exception {
		jdbc.execute("""
				truncate seasons, circuits, drivers, constructors, races, sessions, race_entries, session_results,
				         lap_times, pit_stops, championship_snapshots restart identity cascade""");
		var dump = new ClassPathResource("fixtures/jolpica/dump").getFile().toPath();
		jobOperator.start(jolpicaHistoryImport, HistoryImportService.parameters(dump, System.nanoTime()));

		jdbc.update("insert into seasons (year) values (2023)");
		jdbc.update("insert into circuits (ref, name) values ('marina_bay', 'Marina Bay Street Circuit')");
		raceId = jdbc.queryForObject("""
				insert into races (season_year, round, circuit_id, name, race_date, openf1_meeting_key)
				select 2023, 16, id, 'Singapore Grand Prix', date '2023-09-17', 1219 from circuits
				where ref = 'marina_bay' returning id""", Long.class);
		given(openF1.sessions(1219)).willReturn(Fixtures.openF1Sessions(1219));
		given(openF1.laps(9165)).willReturn(Fixtures.openF1Laps(9165));
		given(openF1.stints(9165)).willReturn(Fixtures.openF1Stints(9165));
		given(openF1.pitStops(9165)).willReturn(Fixtures.openF1PitStops(9165));
		given(openF1.drivers(9165)).willReturn(Fixtures.openF1Drivers(9165));
		given(openF1.raceControl(9165)).willReturn(Fixtures.openF1RaceControl(9165));
		given(openF1.weather(9165)).willReturn(Fixtures.openF1Weather(9165));
		given(openF1.teamRadio(9165)).willReturn(Fixtures.openF1TeamRadio(9165));
		given(f1Archive.trackStatus(2023, 9165)).willReturn(Optional.of(Fixtures.f1ArchiveSingapore2023Race()));
		importer.importRace(raceId);
		sessionId = jdbc.queryForObject("select id from sessions where openf1_session_key = 9165", Long.class);
	}

	@Test
	void everyReadModelRebuiltFromTheEventsAloneGivesTheSameAnswers() {
		Map<String, Object> before = readModels();
		long events = jdbc.queryForObject("select count(*) from event_store", Long.class);

		// what the events were worked out from: gone
		jdbc.execute("""
				delete from penalties; delete from race_events; delete from track_status; delete from tire_stints;
				delete from team_radio; delete from weather_data; delete from session_results""");
		http.post().uri("/api/admin/projections/rebuild").exchange().expectStatus().isOk();

		assertThat(readModels()).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from event_store", Long.class)).isEqualTo(events);
	}

	@Test
	@SuppressWarnings("unchecked")
	void theSessionsEventsCanBeQueriedByTypeAndTime() {
		var pitIns = (List<Map<String, Object>>) get("/api/sessions/" + sessionId + "/events?type=PIT_IN").get("events");
		assertThat(pitIns).hasSize(25).allSatisfy(e -> assertThat(e.get("type")).isEqualTo("PIT_IN"));
		assertThat(pitIns).extracting(e -> (Integer) e.get("seq")).isSorted();

		Map<String, Object> window = get("/api/sessions/" + sessionId
				+ "/events?type=FLAG_CHANGED&from=2023-09-17T12:36:00Z&to=2023-09-17T12:37:00Z");
		assertThat(window).containsEntry("sessionId", (int) sessionId);
		assertThat(window.get("generation")).isEqualTo(window.get("latestGeneration")); // the latest by default
		var flags = (List<Map<String, Object>>) window.get("events");
		assertThat(flags).anySatisfy(e -> {
			assertThat(((Map<String, Object>) e.get("payload")).get("flag")).isEqualTo("SAFETY_CAR");
			assertThat(e.get("at")).isEqualTo("2023-09-17T12:36:40.792Z"); // the F1 archive's exact time
			assertThat(e.get("t")).isInstanceOf(Number.class);
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void timeTravelToTheEventThatBroughtOutTheSafetyCar() {
		var flags = (List<Map<String, Object>>) get("/api/sessions/" + sessionId
				+ "/events?type=FLAG_CHANGED&from=2023-09-17T12:36:00Z&to=2023-09-17T12:37:00Z").get("events");
		int sc = flags.stream().filter(e -> "SAFETY_CAR".equals(((Map<String, Object>) e.get("payload")).get("flag")))
				.map(e -> (Integer) e.get("seq")).findFirst().orElseThrow();

		Map<String, Object> at = get("/api/sessions/" + sessionId + "/events/" + sc + "/state");
		Map<String, Object> justBefore = get("/api/sessions/" + sessionId + "/events/" + (sc - 1) + "/state");

		assertThat(at).containsEntry("seq", sc).containsEntry("type", "FLAG_CHANGED");
		assertThat((Map<String, Object>) at.get("state")).containsEntry("flag", "SAFETY_CAR");
		assertThat((Map<String, Object>) justBefore.get("state")).doesNotContainEntry("flag", "SAFETY_CAR");
	}

	@Test
	void anEventNumberPastTheEndIsNotFound() {
		http.get().uri("/api/sessions/" + sessionId + "/events/999999/state").exchange().expectStatus().isNotFound();
	}

	// every read model's answers, as clients see them
	private Map<String, Object> readModels() {
		Map<String, Object> answers = new LinkedHashMap<>();
		answers.put("timeline", get("/api/races/" + raceId + "/timeline"));
		for (String at : List.of("2023-09-17T12:10:00Z", "2023-09-17T12:40:00Z", "2023-09-17T13:30:00Z")) {
			answers.put("state " + at, get("/api/sessions/" + sessionId + "/replay/state?at=" + at));
		}
		answers.put("snapshots", http.get().uri("/api/sessions/" + sessionId + "/replay/snapshots").exchange()
				.expectStatus().isOk().expectBody(new ParameterizedTypeReference<List<Object>>() {
				}).returnResult().getResponseBody());
		for (int season : List.of(1950, 2008, 2024)) {
			answers.put("standings " + season, get("/api/seasons/" + season + "/standings"));
		}
		answers.put("driver_race_results", jdbc.queryForList("select * from driver_race_results order by race_entry_id"));
		return answers;
	}

	private Map<String, Object> get(String path) {
		return http.get().uri(path).exchange().expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				}).returnResult().getResponseBody();
	}

}

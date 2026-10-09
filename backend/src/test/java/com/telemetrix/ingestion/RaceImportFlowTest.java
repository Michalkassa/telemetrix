package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.Fixtures;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

// Outer test, end to end: a user opens a never-imported race over HTTP; it downloads in the
// background and becomes READY with all its data stored. OpenF1 replays recorded responses.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class RaceImportFlowTest {

	@Autowired
	RestTestClient http;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	OpenF1Client openF1;

	@MockitoBean
	MultiviewerClient multiviewer; // returns no map: tests never call live APIs

	long raceId;

	@BeforeEach
	void aCalendarWithBahrain2024() {
		jdbc.execute("truncate seasons, circuits, races, sessions, laps, tire_stints, session_pit_stops restart identity cascade");
		jdbc.update("insert into seasons (year) values (2024)");
		jdbc.update("insert into circuits (ref, name) values ('bahrain', 'Bahrain International Circuit')");
		raceId = jdbc.queryForObject("""
				insert into races (season_year, round, circuit_id, name, race_date, openf1_meeting_key)
				select 2024, 1, id, 'Bahrain Grand Prix', date '2024-03-02', 1229 from circuits returning id""", Long.class);
		given(openF1.sessions(1229)).willReturn(Fixtures.openF1Sessions(1229));
		given(openF1.laps(9472)).willReturn(Fixtures.openF1Laps(9472));
		given(openF1.stints(9472)).willReturn(Fixtures.openF1Stints(9472));
		given(openF1.pitStops(9472)).willReturn(Fixtures.openF1PitStops(9472));
	}

	@Test
	void openingANeverImportedRaceDownloadsItAndMakesItReady() throws Exception {
		http.get().uri("/api/races/{id}/import-status", raceId).exchange()
				.expectBody().jsonPath("$.state").isEqualTo("NOT_IMPORTED");

		http.post().uri("/api/races/{id}/import", raceId).exchange()
				.expectStatus().isAccepted()
				.expectBody().jsonPath("$.state").isEqualTo("IMPORTING");

		awaitState("READY");
		assertThat(count("sessions")).isEqualTo(5);
		assertThat(count("laps")).isEqualTo(1129);
		assertThat(count("tire_stints")).isEqualTo(63);
		assertThat(count("session_pit_stops")).isEqualTo(43);

		http.post().uri("/api/races/{id}/import", raceId).exchange() // opening it again: instant
				.expectStatus().isOk()
				.expectBody().jsonPath("$.state").isEqualTo("READY");
	}

	// The import runs on a background thread: poll its status (bounded) instead of sleeping blindly.
	private void awaitState(String expected) throws InterruptedException {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
		String state = null;
		while (Instant.now().isBefore(deadline)) {
			state = http.get().uri("/api/races/{id}/import-status", raceId).exchange()
					.expectBody(String.class).returnResult().getResponseBody();
			if (state != null && state.contains("\"" + expected + "\"")) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("Race never became " + expected + "; last status: " + state);
	}

	private long count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Long.class);
	}

}

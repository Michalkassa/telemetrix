package com.telemetrix.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.Fixtures;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.ingestion.RaceImporter;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

// Outer test: the real 2023 Singapore GP (recorded OpenF1 + F1 archive data) imported, then its
// timeline over HTTP. Sainz won; the safety car came out on lap 20, the VSC on lap 44, and Pérez
// collided with Albon (turn 13) and Lawson (turn 7) late on.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class TimelineFlowTest {

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

	long raceId;

	@BeforeEach
	void importSingapore2023() {
		jdbc.execute("truncate seasons, circuits, races, sessions restart identity cascade");
		jdbc.update("insert into seasons (year) values (2023)");
		jdbc.update("insert into circuits (ref, name) values ('marina_bay', 'Marina Bay Street Circuit')");
		raceId = jdbc.queryForObject("""
				insert into races (season_year, round, circuit_id, name, race_date, openf1_meeting_key)
				select 2023, 16, id, 'Singapore Grand Prix', date '2023-09-17', 1219 from circuits returning id""",
				Long.class);
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
	}

	@Test
	void theTimelineShowsTheSafetyCarTheVscAndPerezsCollisionsInTimeOrder() {
		List<Map<String, Object>> events = timeline("");

		assertThat(events).isNotEmpty();
		List<Instant> times = events.stream().map(e -> Instant.parse((String) e.get("at"))).toList();
		assertThat(times).isSorted();
		assertThat(events).filteredOn(e -> e.get("type").equals("SAFETY_CAR")).singleElement()
				.satisfies(sc -> {
					assertThat(sc.get("lap")).isEqualTo(20);
					assertThat(sc.get("endLap")).isEqualTo(22);
				});
		assertThat(events).filteredOn(e -> e.get("type").equals("VSC")).singleElement()
				.satisfies(vsc -> assertThat(vsc.get("lap")).isEqualTo(44));
		assertThat(events).filteredOn(e -> List.of("INCIDENT", "CRASH").contains(e.get("type")))
				.extracting(e -> cars(e))
				.contains(List.of(11, 23), List.of(11, 40));
		assertThat(events).first().satisfies(e -> {
			assertThat(e).containsKeys("t", "severity", "summary", "participants");
		});
	}

	@Test
	void filtersByType() {
		List<Map<String, Object>> events = timeline("?type=SAFETY_CAR&type=VSC");

		assertThat(events).extracting(e -> e.get("type")).containsExactly("SAFETY_CAR", "VSC");
	}

	@Test
	void anUnknownRaceIsNotFound() {
		http.get().uri("/api/races/999999/timeline").exchange().expectStatus().isNotFound();
	}

	@SuppressWarnings("unchecked")
	private static List<Integer> cars(Map<String, Object> event) {
		return ((List<Map<String, Object>>) event.get("participants")).stream()
				.map(p -> (Integer) p.get("number"))
				.toList();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> timeline(String query) {
		Map<String, Object> body = http.get().uri("/api/races/" + raceId + "/timeline" + query).exchange()
				.expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				})
				.returnResult().getResponseBody();
		return (List<Map<String, Object>>) body.get("events");
	}

}

package com.telemetrix.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.Fixtures;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.ingestion.RaceImporter;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1Client;
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

// Outer test: the race state at any moment of the real 2023 Singapore GP — order, gaps, tyres, flags,
// penalties — and the snapshots the browser gets to make scrubbing instant.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class RaceStateFlowTest {

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

	long sessionId;

	@BeforeEach
	void importSingapore2023() {
		jdbc.execute("truncate seasons, circuits, races, sessions restart identity cascade");
		jdbc.update("insert into seasons (year) values (2023)");
		jdbc.update("insert into circuits (ref, name) values ('marina_bay', 'Marina Bay Street Circuit')");
		long raceId = jdbc.queryForObject("""
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
		sessionId = jdbc.queryForObject("select id from sessions where openf1_session_key = 9165", Long.class);
	}

	@Test
	void underTheSafetyCarOnLap21() {
		Map<String, Object> state = state("2023-09-17T12:40:00Z");

		assertThat(state.get("flag")).isEqualTo("SAFETY_CAR");
		assertThat(state.get("lap")).isEqualTo(21);
		List<Map<String, Object>> cars = cars(state);
		assertThat(cars.getFirst()).satisfies(leader -> {
			assertThat(leader.get("position")).isEqualTo(1);
			assertThat(leader.get("code")).isEqualTo("SAI"); // Sainz led from pole
			assertThat(leader.get("gapToLeader")).isEqualTo(0.0);
		});
		assertThat(cars).extracting(c -> c.get("position")).containsExactlyElementsOf(
				java.util.stream.IntStream.rangeClosed(1, cars.size()).boxed().toList());
		assertThat(cars.get(1).get("interval")).isInstanceOf(Double.class);
		assertThat(car(cars, "ALO").get("pitStops")).isEqualTo(1); // pitted under the safety car
		assertThat(car(cars, "ALO").get("compound")).isEqualTo("HARD");
	}

	@Test
	void alonsosPenaltyIsActiveOnceIssued() {
		assertThat(car(cars(state("2023-09-17T12:43:00Z")), "ALO").get("penaltySeconds")).isEqualTo(0);
		assertThat(car(cars(state("2023-09-17T12:44:00Z")), "ALO").get("penaltySeconds")).isEqualTo(5);
	}

	@Test
	void retiredCarsAreRetired() {
		Map<String, Object> ocon = car(cars(state("2023-09-17T13:30:00Z")), "OCO");

		assertThat(ocon.get("status")).isEqualTo("RETIRED");
		assertThat(ocon.get("gapToLeader")).isNull();
	}

	@Test
	void snapshotsComeOncePerLapAndAtKeyEvents() {
		List<Map<String, Object>> snapshots = http.get().uri("/api/sessions/" + sessionId + "/replay/snapshots")
				.exchange().expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {
				}).returnResult().getResponseBody();

		assertThat(snapshots).filteredOn(s -> s.get("reason").equals("LAP")).hasSize(62);
		assertThat(snapshots).filteredOn(s -> s.get("reason").equals("SAFETY_CAR")).hasSize(1);
		assertThat(snapshots).filteredOn(s -> s.get("reason").equals("PIT_STOP")).hasSize(25);
		assertThat(snapshots).extracting(s -> ((Number) s.get("t")).longValue()).isSorted();
	}

	@Test
	void yellowSectorsMatchRaceControlAndTheSafetyCarShows() {
		// 12:35:04 yellow, then double yellow in sector 7; cleared 12:35:24; the safety car from 12:36:41
		assertThat(state("2023-09-17T12:35:10Z").get("yellowSectors")).isEqualTo(Map.of("7", "DOUBLE_YELLOW"));
		assertThat(state("2023-09-17T12:35:30Z").get("yellowSectors")).isEqualTo(Map.of());
		Map<String, Object> underSc = state("2023-09-17T12:38:00Z");
		assertThat(underSc.get("flag")).isEqualTo("SAFETY_CAR");
		assertThat(state("2023-09-17T13:30:00Z").get("weather")).isInstanceOf(Map.class);
	}

	@Test
	void theBrowserGetsASnapshotAtEveryChange() {
		List<Map<String, Object>> snapshots = http.get().uri("/api/sessions/" + sessionId + "/replay/snapshots")
				.exchange().expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {
				}).returnResult().getResponseBody();

		var changes = snapshots.stream().filter(s -> s.get("reason").equals("CHANGE")).toList();
		assertThat(changes).isNotEmpty();
		assertThat(changes).anySatisfy(s -> assertThat(s.get("yellowSectors")).isEqualTo(Map.of("7", "DOUBLE_YELLOW")));
		assertThat(changes).anySatisfy(s -> assertThat(s.get("drsEnabled")).isEqualTo(true));
	}

	@Test
	@SuppressWarnings("unchecked")
	void theReplayCarriesTheWeatherForTheOverlay() {
		Map<String, Object> replay = http.get().uri("/api/sessions/" + sessionId + "/replay").exchange()
				.expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				}).returnResult().getResponseBody();

		List<Map<String, Object>> weather = (List<Map<String, Object>>) replay.get("weather");
		assertThat(weather).hasSize(176);
		// the first reading, 11:01:24.028, an hour before the replay clock's zero (12:03:44.658)
		assertThat(weather.getFirst()).containsEntry("t", -3_740_630).containsEntry("airTemperature", 30.0)
				.containsEntry("trackTemperature", 38.3).containsEntry("rainfall", false);
	}

	@Test
	void anyMomentAnswersQuickly() {
		state("2023-09-17T12:30:00Z"); // warm up: the first request loads the race
		long started = System.nanoTime();
		for (String at : List.of("2023-09-17T13:45:00Z", "2023-09-17T12:05:00Z", "2023-09-17T13:10:31Z")) {
			state(at);
		}
		assertThat((System.nanoTime() - started) / 3 / 1_000_000).isLessThan(100);
	}

	private Map<String, Object> state(String at) {
		return http.get().uri("/api/sessions/" + sessionId + "/replay/state?at=" + at).exchange()
				.expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				}).returnResult().getResponseBody();
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> cars(Map<String, Object> state) {
		return (List<Map<String, Object>>) state.get("cars");
	}

	private static Map<String, Object> car(List<Map<String, Object>> cars, String code) {
		return cars.stream().filter(c -> code.equals(c.get("code"))).findFirst().orElseThrow();
	}

}

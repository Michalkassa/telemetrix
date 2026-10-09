package com.telemetrix.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.Fixtures;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.ingestion.RaceImporter;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

// Outer test: the real 2024 Bahrain GP (recorded OpenF1 + Multiviewer data) imported and replayed,
// checked against the official results from Jolpica.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ReplayFlowTest {

	// Official order at the end of laps 1, 2 and 3, and the final classification (car numbers)
	static final List<Integer> AFTER_LAP_1 = List.of(1, 16, 63, 11, 55, 14, 4, 81, 44, 22, 23, 20, 24, 3, 2, 31, 10, 77, 18, 27);

	static final List<Integer> AFTER_LAP_2 = List.of(1, 16, 63, 11, 55, 14, 4, 81, 44, 22, 23, 20, 24, 2, 3, 31, 10, 77, 18, 27);

	static final List<Integer> AFTER_LAP_3 = List.of(1, 63, 16, 11, 55, 4, 14, 81, 44, 22, 23, 20, 24, 2, 3, 31, 10, 77, 18, 27);

	static final List<Integer> FINAL = List.of(1, 11, 55, 16, 63, 4, 44, 81, 14, 18, 24, 20, 3, 22, 23, 27, 31, 10, 77, 2);

	@Autowired
	RaceImporter importer;

	@Autowired
	ReplayService replays;

	@Autowired
	RestTestClient http;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	OpenF1Client openF1;

	@MockitoBean
	MultiviewerClient multiviewer;

	long sessionId;

	@BeforeEach
	void importBahrain2024() {
		jdbc.execute("""
				truncate seasons, circuits, races, sessions, laps, tire_stints, session_pit_stops, session_drivers,
				         circuit_layouts restart identity cascade""");
		jdbc.update("insert into seasons (year) values (2024)");
		jdbc.update("insert into circuits (ref, name, openf1_circuit_key) values ('bahrain', 'Bahrain International Circuit', 63)");
		long raceId = jdbc.queryForObject("""
				insert into races (season_year, round, circuit_id, name, race_date, openf1_meeting_key)
				select 2024, 1, id, 'Bahrain Grand Prix', date '2024-03-02', 1229 from circuits returning id""", Long.class);
		given(openF1.sessions(1229)).willReturn(Fixtures.openF1Sessions(1229));
		given(openF1.laps(9472)).willReturn(Fixtures.openF1Laps(9472));
		given(openF1.stints(9472)).willReturn(Fixtures.openF1Stints(9472));
		given(openF1.pitStops(9472)).willReturn(Fixtures.openF1PitStops(9472));
		given(openF1.drivers(9472)).willReturn(Fixtures.openF1Drivers(9472));
		given(multiviewer.circuit(63, 2024)).willReturn(Optional.of(Fixtures.multiviewerCircuit(63, 2024)));
		importer.importRace(raceId);
		sessionId = jdbc.queryForObject("select id from sessions where openf1_session_key = 9472", Long.class);
	}

	@Test
	void theOrderInWhichCarsCompleteEachLapIsTheOfficialOrder() {
		var replay = replays.replay(sessionId);

		assertThat(crossingOrder(replay, 1)).isEqualTo(AFTER_LAP_1);
		assertThat(crossingOrder(replay, 2)).isEqualTo(AFTER_LAP_2);
		assertThat(crossingOrder(replay, 3)).isEqualTo(AFTER_LAP_3);
	}

	@Test
	void afterTheFlagTheOrderIsTheFinalClassification() {
		var replay = replays.replay(sessionId);
		assertThat(replay.totalLaps()).isEqualTo(57);
		assertThat(replay.chequeredAt()).isEqualTo(Instant.parse("2024-03-02T16:35:27.617Z"));

		var end = replays.frame(sessionId, replay.chequeredAt().plusSeconds(600));

		assertThat(end.cars()).extracting(PlaybackFrame.Car::number).isEqualTo(FINAL);
		assertThat(end.cars()).allSatisfy(car -> assertThat(car.status()).isEqualTo(PlaybackFrame.Status.FINISHED));
		assertThat(end.cars().getFirst().lap()).isEqualTo(57);
		assertThat(end.cars().getLast().lap()).isEqualTo(55); // Sargeant, two laps down
	}

	@Test
	void atTheMomentTheLeaderCompletesALapItIsOnTheLine() {
		var replay = replays.replay(sessionId);
		Instant maxCompletesLap10 = replay.timeline(1).reachedAt(10.0);

		var frame = replays.frame(sessionId, maxCompletesLap10);

		var max = frame.cars().getFirst();
		assertThat(max.number()).isEqualTo(1);
		assertThat(max.lap()).isEqualTo(11);
		assertThat(max.progress()).isZero();
		assertThat(frame.leaderLap()).isEqualTo(11);
		// on the start/finish line of the map
		var line = replay.outline().pointAt(0);
		assertThat(max.x()).isEqualTo(line.x());
		assertThat(max.y()).isEqualTo(line.y());
	}

	@Test
	void theWholeRaceDownloadsInOneSmallResponse() {
		byte[] body = http.get().uri("/api/sessions/{id}/replay", sessionId).exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.totalLaps").isEqualTo(57)
				.jsonPath("$.drivers.length()").isEqualTo(20)
				.jsonPath("$.drivers[?(@.number == 1)].teamColour").isEqualTo(List.of("3671c6"))
				.jsonPath("$.timing.length()").isEqualTo(20)
				.jsonPath("$.layout.circuit").isEqualTo("bahrain")
				.returnResult().getResponseBody();

		assertThat(body.length).as("response bytes").isLessThan(300_000);
	}

	private static List<Integer> crossingOrder(RaceReplay replay, int lap) {
		return replay.carNumbers().stream()
				.filter(car -> replay.timeline(car).reachedAt(lap) != null)
				.sorted(Comparator.comparing(car -> replay.timeline(car).reachedAt(lap)))
				.toList();
	}

}

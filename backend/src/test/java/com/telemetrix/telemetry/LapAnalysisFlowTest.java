package com.telemetrix.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;

import com.telemetrix.Fixtures;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.ingestion.RaceImporter;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1CarData;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.ingestion.openf1.OpenF1Location;
import java.util.List;
import java.util.Map;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// Outer test: the 2024 Bahrain GP's fastest laps compared — Verstappen's lap 39 (1:32.608) and
// Leclerc's lap 36 (1:34.090), from recorded OpenF1 telemetry. The delta trace ends at their lap-time
// difference.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class LapAnalysisFlowTest {

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
	void importBahrain2024() {
		jdbc.execute("truncate seasons, circuits, races, sessions restart identity cascade");
		jdbc.update("insert into seasons (year) values (2024)");
		jdbc.update("insert into circuits (ref, name) values ('bahrain', 'Bahrain International Circuit')");
		long raceId = jdbc.queryForObject("""
				insert into races (season_year, round, circuit_id, name, race_date, openf1_meeting_key)
				select 2024, 1, id, 'Bahrain Grand Prix', date '2024-03-02', 1229 from circuits returning id""", Long.class);
		given(openF1.sessions(1229)).willReturn(Fixtures.openF1Sessions(1229));
		given(openF1.laps(9472)).willReturn(Fixtures.openF1Laps(9472));
		given(openF1.stints(9472)).willReturn(Fixtures.openF1Stints(9472));
		given(openF1.pitStops(9472)).willReturn(Fixtures.openF1PitStops(9472));
		given(openF1.drivers(9472)).willReturn(Fixtures.openF1Drivers(9472));
		importer.importRace(raceId);
		sessionId = jdbc.queryForObject("select id from sessions where openf1_session_key = 9472", Long.class);
		// the telemetry downloaded on demand: only around the two laps here
		given(openF1.carData(9472, 1)).willReturn(read("car_data_9472_1_lap39.json", new TypeReference<List<OpenF1CarData>>() {
		}));
		given(openF1.locations(9472, 1)).willReturn(read("location_9472_1_lap39.json", new TypeReference<List<OpenF1Location>>() {
		}));
		given(openF1.carData(9472, 16)).willReturn(read("car_data_9472_16_lap36.json", new TypeReference<List<OpenF1CarData>>() {
		}));
		given(openF1.locations(9472, 16)).willReturn(read("location_9472_16_lap36.json", new TypeReference<List<OpenF1Location>>() {
		}));
	}

	@Test
	@SuppressWarnings("unchecked")
	void comparingTheFastestLapsTheDeltaEndsAtTheirLapTimeDifference() {
		Map<String, Object> compare = get("/telemetry/compare?driver=1&vs=16"); // each driver's fastest lap

		Map<String, Object> reference = (Map<String, Object>) compare.get("reference");
		Map<String, Object> other = (Map<String, Object>) compare.get("other");
		assertThat(reference.get("lap")).isEqualTo(39);
		assertThat(other.get("lap")).isEqualTo(36);
		assertThat(compare.get("lapDelta")).isEqualTo(1.482);
		List<Map<String, Object>> delta = (List<Map<String, Object>>) compare.get("delta");
		assertThat(((Number) delta.getLast().get("delta")).doubleValue()).isCloseTo(1.482, within(1e-9));
		assertThat(((Number) delta.getFirst().get("delta")).doubleValue()).isZero();
		assertThat(compare.get("sectorDeltas")).isEqualTo(List.of(0.322, 0.734, 0.426));
	}

	@Test
	@SuppressWarnings("unchecked")
	void oneLapAgainstDistanceWithItsTyresAndEstimates() {
		Map<String, Object> lap = get("/telemetry/lap?driver=1&lap=39");

		assertThat(lap.get("lapTime")).isEqualTo(92.608);
		assertThat(lap.get("sectors")).isEqualTo(List.of(29.741, 39.916, 22.951));
		List<Map<String, Object>> trace = (List<Map<String, Object>>) lap.get("trace");
		assertThat(trace).hasSizeGreaterThan(300);
		double distance = ((Number) trace.getLast().get("distance")).doubleValue();
		assertThat(distance).isCloseTo(5412, within(5412 * 0.03)); // Bahrain: 5.412 km a lap
		assertThat(trace.getFirst()).containsKeys("speed", "throttle", "brake", "gear", "rpm", "drs", "time");
		Map<String, Object> tyre = (Map<String, Object>) lap.get("tyre");
		assertThat(tyre).containsEntry("compound", "SOFT").containsEntry("stint", 3).containsEntry("age", 1);
		Map<String, Object> estimates = (Map<String, Object>) lap.get("estimates");
		assertThat(estimates).containsKeys("fuelKg", "ersDeployedMj", "ersHarvestedMj", "note");
		assertThat((String) estimates.get("note")).containsIgnoringCase("estimate");
	}

	@Test
	void aLapTheCarDidntDoIsNotFound() {
		http.get().uri("/api/sessions/" + sessionId + "/telemetry/lap?driver=1&lap=99").exchange()
				.expectStatus().isNotFound();
	}

	private Map<String, Object> get(String path) {
		return http.get().uri("/api/sessions/" + sessionId + path).exchange().expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				}).returnResult().getResponseBody();
	}

	private static <T> T read(String file, TypeReference<T> type) {
		try (var in = new org.springframework.core.io.ClassPathResource("fixtures/openf1/" + file).getInputStream()) {
			return JsonMapper.builder().build().readValue(in, type);
		}
		catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

}

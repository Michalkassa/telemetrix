package com.telemetrix.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.telemetrix.Fixtures;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.cache.Caches;
import com.telemetrix.ingestion.RaceImporter;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1CarData;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.ingestion.openf1.OpenF1Location;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// The hot telemetry is cached in Redis (docs/adr/0008): a lap, once read, is served without touching the
// stored points again — on any instance. A whole session's telemetry is too big to cache.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TelemetryCacheTest {

	@Autowired
	RaceImporter importer;

	@Autowired
	TelemetryService telemetry;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	CacheManager caches;

	@MockitoSpyBean
	TelemetryStore store;

	@MockitoBean
	OpenF1Client openF1;

	@MockitoBean
	MultiviewerClient multiviewer;

	@MockitoBean
	F1ArchiveClient f1Archive;

	long sessionId;

	@BeforeEach
	void importBahrain2024WithVerstappensLap39() {
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
		importer.importRace(raceId); // evicts lap telemetry: each test starts cold
		sessionId = jdbc.queryForObject("select id from sessions where openf1_session_key = 9472", Long.class);
		given(openF1.carData(9472, 1)).willReturn(read("car_data_9472_1_lap39.json", new TypeReference<List<OpenF1CarData>>() {
		}));
		given(openF1.locations(9472, 1)).willReturn(read("location_9472_1_lap39.json", new TypeReference<List<OpenF1Location>>() {
		}));
		clearInvocations(store);
	}

	@Test
	void aLapReadTwiceReadsTheStoredPointsOnce() {
		var first = telemetry.telemetry(sessionId, 1, 39);
		var second = telemetry.telemetry(sessionId, 1, 39);

		assertThat(second).isEqualTo(first);
		assertThat(second.points()).isNotEmpty();
		verify(store, times(1)).points(anyLong(), anyInt(), any(), any());
		assertThat(caches.getCache(Caches.TELEMETRY_LAPS).get(sessionId + ":1:39")).isNotNull();
	}

	@Test
	void aWholeSessionIsNotCached() {
		telemetry.telemetry(sessionId, 1, null);
		telemetry.telemetry(sessionId, 1, null);

		verify(store, times(2)).points(anyLong(), anyInt(), any(), any());
		assertThat(caches.getCache(Caches.TELEMETRY_LAPS).get(sessionId + ":1:null")).isNull();
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

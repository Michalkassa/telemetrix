package com.telemetrix.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.telemetrix.Fixtures;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.eventstore.EventStore;
import com.telemetrix.eventstore.Projections;
import com.telemetrix.eventstore.StreamAppended;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamType;
import com.telemetrix.ingestion.RaceImported;
import com.telemetrix.ingestion.RaceImporter;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.history.HistoryImportService;
import com.telemetrix.ingestion.history.HistoryImported;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

// Outer test for Step 38 (docs/adr/0008): the expensive reads are cached in Redis — a race's timeline, its
// replay and playback snapshots, and the championship standings — so the second request is a cache hit
// (Redis cache statistics: the Check-it's "metrics"), on this instance or any other. An import, a new
// generation of the events, or a rebuild evicts what it changed.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class CachingFlowTest {

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

	@Autowired
	CacheManager caches;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	Projections projections;

	@MockitoSpyBean
	EventStore store;

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
		importer.importRace(raceId); // the import evicts this race's caches: each test starts cold
		sessionId = jdbc.queryForObject("select id from sessions where openf1_session_key = 9165", Long.class);
	}

	@Test
	void theSecondTimelineRequestDoesNotReachTheEventStore() {
		var stream = new StreamId(StreamType.SESSION, sessionId);
		clearInvocations(store);
		var before = stats(Caches.TIMELINES);

		var first = get("/api/races/" + raceId + "/timeline");
		var second = get("/api/races/" + raceId + "/timeline");

		assertThat(second).isEqualTo(first);
		verify(store, times(1)).read(eq(stream));
		assertThat(stats(Caches.TIMELINES).getHits() - before.getHits()).isEqualTo(1);
	}

	@Test
	void theReplayAndItsSnapshotsAreServedFromTheCacheTheSecondTime() {
		long replayHits = stats(Caches.REPLAYS).getHits();
		long snapshotHits = stats(Caches.SNAPSHOTS).getHits();

		assertThat(get("/api/sessions/" + sessionId + "/replay")).isEqualTo(get("/api/sessions/" + sessionId + "/replay"));
		assertThat(getList("/api/sessions/" + sessionId + "/replay/snapshots"))
				.isEqualTo(getList("/api/sessions/" + sessionId + "/replay/snapshots"));

		assertThat(stats(Caches.REPLAYS).getHits() - replayHits).isEqualTo(1);
		assertThat(stats(Caches.SNAPSHOTS).getHits() - snapshotHits).isEqualTo(1);
	}

	@Test
	void standingsAreServedFromTheCacheTheSecondTime() {
		long hits = stats(Caches.STANDINGS).getHits();

		assertThat(get("/api/seasons/2024/standings")).isEqualTo(get("/api/seasons/2024/standings"));

		assertThat(stats(Caches.STANDINGS).getHits() - hits).isEqualTo(1);
	}

	@Test
	void aRaceImportEvictsThatRacesTimelineReplayAndSnapshots() {
		get("/api/races/" + raceId + "/timeline");
		get("/api/sessions/" + sessionId + "/replay");
		getList("/api/sessions/" + sessionId + "/replay/snapshots");

		events.publishEvent(new RaceImported(raceId, List.of(sessionId)));

		assertThat(caches.getCache(Caches.TIMELINES).get(sessionId)).isNull();
		assertThat(caches.getCache(Caches.REPLAYS).get(sessionId)).isNull();
		assertThat(caches.getCache(Caches.SNAPSHOTS).get(sessionId)).isNull();
	}

	@Test
	void aNewGenerationOfTheSessionsEventsEvictsItsTimelineAndSnapshots() {
		get("/api/races/" + raceId + "/timeline");
		getList("/api/sessions/" + sessionId + "/replay/snapshots");

		events.publishEvent(new StreamAppended(new StreamId(StreamType.SESSION, sessionId), 2));

		assertThat(caches.getCache(Caches.TIMELINES).get(sessionId)).isNull();
		assertThat(caches.getCache(Caches.SNAPSHOTS).get(sessionId)).isNull();
	}

	@Test
	void anotherSessionsNewEventsLeaveThisOnesTimelineAlone() {
		get("/api/races/" + raceId + "/timeline");

		events.publishEvent(new StreamAppended(new StreamId(StreamType.SESSION, sessionId + 1000), 2));

		assertThat(caches.getCache(Caches.TIMELINES).get(sessionId)).isNotNull();
	}

	@Test
	void aHistoryImportEvictsTheStandings() {
		get("/api/seasons/2024/standings");
		assertThat(caches.getCache(Caches.STANDINGS).get("2024:latest")).isNotNull();

		events.publishEvent(new HistoryImported(1));

		assertThat(caches.getCache(Caches.STANDINGS).get("2024:latest")).isNull();
	}

	@Test
	void rebuildingTheReadModelsEmptiesTheirCaches() {
		get("/api/races/" + raceId + "/timeline");
		get("/api/seasons/2024/standings");

		projections.rebuildAll();

		assertThat(caches.getCache(Caches.TIMELINES).get(sessionId)).isNull();
		assertThat(caches.getCache(Caches.STANDINGS).get("2024:latest")).isNull();
	}

	private org.springframework.data.redis.cache.CacheStatistics stats(String cache) {
		return ((RedisCache) caches.getCache(cache)).getStatistics();
	}

	private java.util.Map<String, Object> get(String path) {
		return http.get().uri(path).exchange().expectStatus().isOk()
				.expectBody(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {
				}).returnResult().getResponseBody();
	}

	private List<Object> getList(String path) {
		return http.get().uri(path).exchange().expectStatus().isOk()
				.expectBody(new org.springframework.core.ParameterizedTypeReference<List<Object>>() {
				}).returnResult().getResponseBody();
	}

}

package com.telemetrix.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.RepositoryTest;
import com.telemetrix.eventstore.EventStore;
import com.telemetrix.eventstore.StoredEvent;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

// The write side of a race's results (docs/adr/0006): its stream opens with the race's conditions (wet:
// rain in its Race session), then a line per car per Grand Prix and sprint, as the history import left
// them. Appended only when something changed. On the hand-checked mini-dataset (analytics/mini-dataset.sql).
@RepositoryTest
@Import({ ResultsRecorder.class, EventStore.class, ResultsRecorderTest.Beans.class })
@Sql("/analytics/mini-dataset.sql")
class ResultsRecorderTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class Beans {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-10-12T20:00:00Z"), ZoneOffset.UTC);
		}

	}

	static final StreamId RACE_1 = new StreamId(StreamType.RACE_RESULTS, 1);

	@Autowired
	ResultsRecorder recorder;

	@Autowired
	EventStore store;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void aRaceIsItsConditionsThenEveryResultLine() {
		recorder.record(1);

		var events = store.read(RACE_1);
		assertThat(events).extracting(StoredEvent::type).containsExactly("RACE_RESULTS_RECORDED", "RESULT_RECORDED",
				"RESULT_RECORDED", "RESULT_RECORDED", "RESULT_RECORDED");
		assertThat(events.getFirst().payload().get("wet").asBoolean()).isTrue();
		var winner = events.get(1).payload();
		assertThat(winner.get("sessionType").asString()).isEqualTo("R");
		assertThat(winner.get("driverRef").asString()).isEqualTo("hamilton");
		assertThat(winner.get("constructorRef").asString()).isEqualTo("mercedes");
		assertThat(winner.get("raceEntryId").asLong()).isEqualTo(11);
		assertThat(winner.get("position").asInt()).isEqualTo(1);
		assertThat(winner.get("grid").asInt()).isEqualTo(1);
		assertThat(winner.get("points").asDouble()).isEqualTo(26.0);
		assertThat(winner.get("classified").asBoolean()).isTrue();
		assertThat(winner.get("fastestLapRank").asInt()).isEqualTo(1);
		assertThat(events.getLast().payload().get("status").asString()).isEqualTo("RETIRED_MECHANICAL");
	}

	@Test
	void aSprintLineIsRecordedAndQualifyingIsNot() {
		jdbc.update("""
				insert into session_results (race_entry_id, session_type, session_number, position, points, classified)
				values (12, 'SR', 1, 1, 8, true), (11, 'Q1', 1, 1, null, null)""");

		recorder.record(1);

		assertThat(store.read(RACE_1)).extracting(e -> e.payload().path("sessionType").asString(""))
				.containsExactly("", "R", "R", "R", "R", "SR");
	}

	@Test
	void recordingAnUnchangedRaceAppendsNothing() {
		recorder.record(1);
		recorder.record(1);

		assertThat(store.generation(RACE_1)).isEqualTo(1);
	}

	@Test
	void aChangedResultIsANewGeneration() {
		recorder.record(1);
		jdbc.update("update session_results set points = 25 where race_entry_id = 11 and session_type = 'R'");

		recorder.record(1);

		assertThat(store.generation(RACE_1)).isEqualTo(2);
		assertThat(store.read(RACE_1).get(1).payload().get("points").asDouble()).isEqualTo(25.0);
	}

	@Test
	void aRaceWithoutWeatherHasUnknownConditions() {
		jdbc.update("delete from weather_data where session_id = 2");

		recorder.record(2);

		assertThat(store.read(new StreamId(StreamType.RACE_RESULTS, 2)).getFirst().payload().get("wet").isNull())
				.isTrue();
	}

	@Test
	void recordingASeasonRecordsEachOfItsRaces() {
		recorder.recordSeason(2024);

		assertThat(store.generation(RACE_1)).isEqualTo(1);
		assertThat(store.generation(new StreamId(StreamType.RACE_RESULTS, 2))).isEqualTo(1);
		assertThat(store.generation(new StreamId(StreamType.RACE_RESULTS, 3))).isZero();
	}

	@Test
	void recordingEverythingRecordsEveryRace() {
		recorder.recordAll();

		assertThat(store.generation(new StreamId(StreamType.RACE_RESULTS, 3))).isEqualTo(1);
	}

	@Test
	void readingARaceThatWasNeverRecordedRecordsIt() {
		assertThat(store.read(new StreamId(StreamType.RACE_RESULTS, 2))).hasSize(5);
	}

}

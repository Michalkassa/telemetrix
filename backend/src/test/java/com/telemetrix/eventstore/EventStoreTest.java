package com.telemetrix.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemetrix.RepositoryTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

// The event store (docs/adr/0006): streams of events that are only ever appended to. A stream worked out
// again (a race imported again) gets a new generation; reads see the latest one. Each test is rolled back.
@RepositoryTest
@Import({ EventStore.class, EventStoreTest.Beans.class })
class EventStoreTest {

	static final StreamId SESSION = new StreamId(StreamType.SESSION, 9165);

	static final Instant T = Instant.parse("2023-09-17T12:40:00Z");

	@TestConfiguration(proxyBeanMethods = false)
	static class Beans {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-10-12T20:00:00Z"), ZoneOffset.UTC);
		}

		@Bean
		Recorder recorder() {
			return new Recorder();
		}

	}

	/** Records a SESSION stream that has none yet: one event, so the test sees it was asked. */
	static class Recorder implements StreamRecorder {

		@Override
		public StreamType type() {
			return StreamType.SESSION;
		}

		@Override
		public void record(long id, EventStore store) {
			store.append(new StreamId(StreamType.SESSION, id), List.of(new NewEvent("RECORDED_ON_DEMAND", null, Map.of())));
		}

	}

	@Autowired
	EventStore store;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void appendsAStreamAsItsFirstGenerationNumberedFromOne() {
		int generation = store.append(SESSION, List.of(
				new NewEvent("FLAG_CHANGED", T, Map.of("flag", "SAFETY_CAR")),
				new NewEvent("PIT_IN", T.plusSeconds(5), Map.of("car", 14))));

		assertThat(generation).isEqualTo(1);
		List<StoredEvent> events = store.read(SESSION);
		assertThat(events).extracting(StoredEvent::seq, StoredEvent::type, StoredEvent::occurredAt)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(1, "FLAG_CHANGED", T),
						org.assertj.core.groups.Tuple.tuple(2, "PIT_IN", T.plusSeconds(5)));
		assertThat(events.getFirst().payload().get("flag").asString()).isEqualTo("SAFETY_CAR");
		assertThat(events.get(1).payload().get("car").asInt()).isEqualTo(14);
		assertThat(events).allSatisfy(e -> {
			assertThat(e.stream()).isEqualTo(SESSION);
			assertThat(e.generation()).isEqualTo(1);
		});
	}

	@Test
	void appendingAgainStartsANewGenerationAndTheOldOneStaysReadable() {
		store.append(SESSION, List.of(new NewEvent("FLAG_CHANGED", T, Map.of("flag", "YELLOW"))));

		int second = store.append(SESSION, List.of(new NewEvent("FLAG_CHANGED", T, Map.of("flag", "RED")),
				new NewEvent("FLAG_CHANGED", T.plusSeconds(60), Map.of("flag", "GREEN"))));

		assertThat(second).isEqualTo(2);
		assertThat(store.generation(SESSION)).isEqualTo(2);
		assertThat(store.read(SESSION)).extracting(e -> e.payload().get("flag").asString()).containsExactly("RED", "GREEN");
		assertThat(store.read(SESSION, 1)).extracting(e -> e.payload().get("flag").asString()).containsExactly("YELLOW");
	}

	@Test
	void streamsAreSeparate() {
		store.append(SESSION, List.of(new NewEvent("PIT_IN", T, Map.of("car", 1))));
		var results = new StreamId(StreamType.RACE_RESULTS, 9165);

		store.append(results, List.of(new NewEvent("RESULT_RECORDED", null, Map.of("position", 1))));

		assertThat(store.read(SESSION)).extracting(StoredEvent::type).containsExactly("PIT_IN");
		assertThat(store.read(results)).extracting(StoredEvent::type).containsExactly("RESULT_RECORDED");
		assertThat(store.read(results).getFirst().occurredAt()).isNull();
	}

	@Test
	void anUpdateIsRejected() {
		store.append(SESSION, List.of(new NewEvent("PIT_IN", T, Map.of("car", 1))));

		assertThatThrownBy(() -> jdbc.update("update event_store set type = 'PIT_OUT'"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("append-only");
	}

	@Test
	void aDeleteIsRejected() {
		store.append(SESSION, List.of(new NewEvent("PIT_IN", T, Map.of("car", 1))));

		assertThatThrownBy(() -> jdbc.update("delete from event_store"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("append-only");
	}

	@Test
	void aTruncateIsRejected() {
		assertThatThrownBy(() -> jdbc.execute("truncate event_store"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("append-only");
	}

	@Test
	void readingAStreamThatWasNeverRecordedRecordsItFirst() {
		var unrecorded = new StreamId(StreamType.SESSION, 777);

		assertThat(store.read(unrecorded)).extracting(StoredEvent::type).containsExactly("RECORDED_ON_DEMAND");
		assertThat(store.generation(unrecorded)).isEqualTo(1);
	}

	@Test
	void aStreamTypeWithoutARecorderReadsAsEmpty() {
		assertThat(store.read(new StreamId(StreamType.RACE_RESULTS, 777))).isEmpty();
		assertThat(store.generation(new StreamId(StreamType.RACE_RESULTS, 777))).isZero();
	}

	@Test
	void queriesByTypeAndTimeWindow() {
		store.append(SESSION, List.of(
				new NewEvent("FLAG_CHANGED", T, Map.of("flag", "SAFETY_CAR")),
				new NewEvent("PIT_IN", T.plusSeconds(10), Map.of("car", 14)),
				new NewEvent("PIT_IN", T.plusSeconds(100), Map.of("car", 1)),
				new NewEvent("PIT_OUT", T.plusSeconds(30), Map.of("car", 14))));

		assertThat(store.query(SESSION, null, Set.of("PIT_IN", "PIT_OUT"), T.plusSeconds(5), T.plusSeconds(60)))
				.extracting(StoredEvent::seq).containsExactly(2, 4);
		assertThat(store.query(SESSION, null, Set.of(), null, null)).hasSize(4);
	}

}

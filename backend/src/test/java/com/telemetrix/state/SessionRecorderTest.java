package com.telemetrix.state;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.event.EventDetector;
import com.telemetrix.event.EventType;
import com.telemetrix.event.RaceDataLoader;
import com.telemetrix.event.RaceEvent;
import com.telemetrix.event.RaceEventDetected;
import com.telemetrix.event.Severity;
import com.telemetrix.event.TestRace;
import com.telemetrix.eventstore.EventStore;
import com.telemetrix.eventstore.NewEvent;
import com.telemetrix.eventstore.StoredEvent;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamType;
import com.telemetrix.ingestion.RaceImported;
import com.telemetrix.racecontrol.TrackStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

// The write side of a race session: its stream is worked out from the imported data and every detector's
// events, and appended only when it changed. Each detected event is published (the Observer pattern) when
// it's first recorded — not again for a race recorded again without changes.
@SpringJUnitConfig(SessionRecorderTest.Config.class)
@RecordApplicationEvents
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SessionRecorderTest {

	static final StreamId SESSION = new StreamId(StreamType.SESSION, 7);

	static final RaceEvent SC = new RaceEvent(EventType.SAFETY_CAR, t(250), 3, t(480), 5, Severity.HIGH, List.of(),
			"Safety car", null);

	static final RaceEvent PIT = new RaceEvent(EventType.PIT_STOP, t(100), 2, t(125), 2, Severity.LOW, List.of(1),
			"Pit stop: VER", null);

	@Configuration
	@Import(SessionRecorder.class)
	static class Config {

		@Bean
		EventDetector safetyCars() {
			return race -> List.of(SC);
		}

		@Bean
		EventDetector pitStops() {
			return race -> List.of(PIT);
		}

		@Bean
		PlatformTransactionManager transactions() { // no database here
			return new PlatformTransactionManager() {

				@Override
				public TransactionStatus getTransaction(TransactionDefinition definition) {
					return new SimpleTransactionStatus();
				}

				@Override
				public void commit(TransactionStatus status) {
				}

				@Override
				public void rollback(TransactionStatus status) {
				}

			};
		}

	}

	@MockitoBean
	RaceDataLoader loader;

	@MockitoBean
	EventStore store;

	@Autowired
	SessionRecorder recorder;

	@Autowired
	ApplicationEvents published;

	TestRace race = new TestRace().session(7).car(1, "VER", 0, 0, 1000, 10).status(TrackStatus.SAFETY_CAR, 250, 480.0);

	@Test
	void recordsTheStreamWithEveryDetectorsEventsInTimeOrder() {
		given(loader.load(7)).willReturn(race.build());

		recorder.record(7, store);

		List<NewEvent> appended = appended();
		assertThat(appended).extracting(NewEvent::type).containsExactly("SESSION_RECORDED", "RACE_EVENT_DETECTED",
				"FLAG_CHANGED", "RACE_EVENT_DETECTED", "FLAG_CHANGED"); // at one moment: the state change first
		assertThat(appended.stream().filter(e -> e.type().equals("RACE_EVENT_DETECTED")).map(NewEvent::payload))
				.containsExactly(PIT, SC);
	}

	@Test
	void publishesEachDetectedEventWhenItIsRecorded() {
		given(loader.load(7)).willReturn(race.build());

		recorder.record(7, store);

		assertThat(published.stream(RaceEventDetected.class)).extracting(RaceEventDetected::event).containsExactly(PIT, SC);
		assertThat(published.stream(RaceEventDetected.class)).allSatisfy(e -> assertThat(e.sessionId()).isEqualTo(7));
	}

	@Test
	void anUnchangedRaceIsNotAppendedOrPublishedAgain() {
		given(loader.load(7)).willReturn(race.build());
		alreadyStored(SessionEvents.derive(race.build(), List.of(PIT, SC)));

		recorder.record(7, store);

		verify(store, never()).append(any(), anyList());
		assertThat(published.stream(RaceEventDetected.class)).isEmpty();
	}

	@Test
	void aChangedRaceIsAppendedAsANewGenerationAndItsEventsPublishedAgain() {
		alreadyStored(SessionEvents.derive(race.build(), List.of(PIT)));
		given(loader.load(7)).willReturn(race.build());

		recorder.onRaceImported(new RaceImported(3, List.of(7L)));

		assertThat(appended()).isNotEmpty();
		assertThat(published.stream(RaceEventDetected.class)).hasSize(2);
	}

	@Test
	void anImportedSessionWithoutTimingIsSkipped() {
		willThrow(new NotFoundException("Replay of session", 8)).given(loader).load(8);
		given(loader.load(7)).willReturn(race.build());

		recorder.onRaceImported(new RaceImported(3, List.of(8L, 7L)));

		verify(store).append(eq(SESSION), anyList());
		verify(store, never()).append(eq(new StreamId(StreamType.SESSION, 8)), anyList());
	}

	private void alreadyStored(List<SessionEvent> events) {
		List<StoredEvent> stored = new ArrayList<>();
		events.forEach(e -> stored.add(SessionEventCodecTest.asStored(SessionEventCodec.encode(e))));
		given(store.generation(SESSION)).willReturn(1);
		given(store.read(SESSION, 1)).willReturn(stored);
	}

	@SuppressWarnings("unchecked")
	private List<NewEvent> appended() {
		ArgumentCaptor<List<NewEvent>> captor = ArgumentCaptor.forClass(List.class);
		verify(store).append(eq(SESSION), captor.capture());
		return captor.getValue();
	}

}

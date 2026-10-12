package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.telemetrix.eventstore.EventStore;
import com.telemetrix.eventstore.StoredEvent;
import com.telemetrix.eventstore.StreamAppended;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// The timeline is a read model of the session's stream: its detected events in stream order, with the
// clock's zero and the drivers' codes from the stream's opening. Kept until the stream is appended to.
// (Running the detectors and publishing what they find is the write side's: SessionRecorderTest.)
class TimelineServiceTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static final StreamId SESSION = new StreamId(StreamType.SESSION, 7);

	static final RaceEvent PIT = new RaceEvent(EventType.PIT_STOP, t(100), 2, t(125), 2, Severity.LOW, List.of(1),
			"Pit stop: VER", null);

	static final RaceEvent SC = new RaceEvent(EventType.SAFETY_CAR, t(250), 3, t(480), 5, Severity.HIGH, List.of(),
			"Safety car", null);

	EventStore store = org.mockito.Mockito.mock(EventStore.class);

	TimelineService timelines = new TimelineService(store);

	@Test
	void theDetectedEventsOfTheStreamInOrder() {
		given(store.read(SESSION)).willReturn(stream());

		Timeline timeline = timelines.timeline(7);

		assertThat(timeline.sessionId()).isEqualTo(7);
		assertThat(timeline.clockStart()).isEqualTo(t(0));
		assertThat(timeline.codes()).isEqualTo(Map.of(1, "VER"));
		assertThat(timeline.events()).containsExactly(PIT, SC);
	}

	@Test
	void keptUntilTheStreamIsAppendedTo() {
		given(store.read(SESSION)).willReturn(stream());

		timelines.timeline(7);
		timelines.timeline(7);
		timelines.onStreamAppended(new StreamAppended(new StreamId(StreamType.RACE_RESULTS, 7), 2));
		timelines.timeline(7);
		verify(store, times(1)).read(SESSION);

		timelines.onStreamAppended(new StreamAppended(SESSION, 2));
		timelines.timeline(7);
		verify(store, times(2)).read(SESSION);
	}

	private static List<StoredEvent> stream() {
		return List.of(stored(1, "SESSION_RECORDED", null, Map.of("clockStart", t(0), "chequeredAt", t(1000),
						"totalLaps", 10, "codes", Map.of(1, "VER"))),
				stored(2, RaceEvent.STORED_TYPE, t(100), PIT),
				stored(3, "FLAG_CHANGED", t(250), Map.of("at", t(250), "flag", "SAFETY_CAR")),
				stored(4, RaceEvent.STORED_TYPE, t(250), SC));
	}

	private static StoredEvent stored(int seq, String type, java.time.Instant at, Object payload) {
		return new StoredEvent(seq, SESSION, 1, seq, type, at, JSON.readTree(JSON.writeValueAsString(payload)), t(0));
	}

}

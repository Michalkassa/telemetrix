package com.telemetrix.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.eventstore.StoredEvent;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

// The session's event log over HTTP: query it (historical querying) and the state at any event (time travel).
@WebMvcTest(SessionEventsController.class)
class SessionEventsControllerTest {

	static final Instant ZERO = Instant.parse("2023-09-17T12:03:44.658Z");

	static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	SessionLog log;

	@Test
	void eventsWithTheirNumberTypeTimeAndPayload() {
		var stream = new StreamId(StreamType.SESSION, 5);
		given(log.events(5, 1, Set.of("PIT_IN", "PIT_OUT"), Instant.parse("2023-09-17T12:30:00Z"), null))
				.willReturn(new SessionLog.Events(5, 1, 2, ZERO, List.of(new StoredEvent(901, stream, 1, 42, "PIT_IN",
						ZERO.plusMillis(1500), JSON.readTree("{\"at\": \"2023-09-17T12:03:46.158Z\", \"car\": 14}"),
						Instant.parse("2026-10-12T20:00:00Z")))));

		assertThat(mvc.get().uri("/api/sessions/5/events?type=PIT_IN&type=PIT_OUT&from=2023-09-17T12:30:00Z&generation=1"))
				.hasStatusOk().bodyJson().isStrictlyEqualTo("""
						{"sessionId": 5, "generation": 1, "latestGeneration": 2, "clockStart": "2023-09-17T12:03:44.658Z",
						 "events": [{"seq": 42, "type": "PIT_IN", "at": "2023-09-17T12:03:46.158Z", "t": 1500,
						             "payload": {"at": "2023-09-17T12:03:46.158Z", "car": 14}}]}
						""");
	}

	@Test
	void anEventBeforeTheStartHasNoTime() {
		var stream = new StreamId(StreamType.SESSION, 5);
		given(log.events(5, null, Set.of(), null, null)).willReturn(new SessionLog.Events(5, 1, 1, ZERO,
				List.of(new StoredEvent(1, stream, 1, 2, "TYRES_FITTED", null, JSON.readTree("{\"car\": 1}"), ZERO))));

		assertThat(mvc.get().uri("/api/sessions/5/events")).hasStatusOk().bodyJson()
				.extractingPath("$.events[0].t").isNull();
	}

	@Test
	void theStateRightAfterAnEvent() {
		var state = new RaceState(ZERO, 1, Flag.SAFETY_CAR, java.util.Map.of(), false, null, List.of());
		given(log.stateAtEvent(5, 120)).willReturn(new SessionLog.EventState(120, "FLAG_CHANGED", ZERO, state));

		assertThat(mvc.get().uri("/api/sessions/5/events/120/state")).hasStatusOk().bodyJson()
				.isLenientlyEqualTo("""
						{"seq": 120, "type": "FLAG_CHANGED", "at": "2023-09-17T12:03:44.658Z",
						 "state": {"lap": 1, "flag": "SAFETY_CAR", "cars": []}}
						""");
	}

	@Test
	void anUnknownEventIsNotFound() {
		given(log.stateAtEvent(5, 9999)).willThrow(new NotFoundException("Event #9999 of session", 5));

		assertThat(mvc.get().uri("/api/sessions/5/events/9999/state")).hasStatus(HttpStatus.NOT_FOUND);
	}

}

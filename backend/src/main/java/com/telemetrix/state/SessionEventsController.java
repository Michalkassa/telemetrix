package com.telemetrix.state;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
class SessionEventsController {

	/** `t`: milliseconds after clockStart; null for what holds from before the start. */
	record EventResponse(int seq, String type, Instant at, Long t, JsonNode payload) {
	}

	record EventsResponse(long sessionId, int generation, int latestGeneration, Instant clockStart,
			List<EventResponse> events) {
	}

	private final SessionLog log;

	SessionEventsController(SessionLog log) {
		this.log = log;
	}

	// The session's stored events in order: ?type=PIT_IN&type=PIT_OUT keeps those types, ?from= and ?to=
	// (inclusive) a time window, ?generation= an earlier version of the stream (before a re-import).
	@GetMapping("/api/sessions/{id}/events")
	EventsResponse events(@PathVariable long id, @RequestParam(name = "type", required = false) Set<String> types,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(required = false) Integer generation) {
		var events = log.events(id, generation, types == null ? Set.of() : types, from, to);
		long zero = events.clockStart().toEpochMilli();
		return new EventsResponse(id, events.generation(), events.latestGeneration(), events.clockStart(),
				events.events().stream()
						.map(e -> new EventResponse(e.seq(), e.type(), e.occurredAt(),
								e.occurredAt() == null ? null : e.occurredAt().toEpochMilli() - zero, e.payload()))
						.toList());
	}

	// Time travel: the whole race state right after event #seq, and nothing after it.
	@GetMapping("/api/sessions/{id}/events/{seq}/state")
	SessionLog.EventState stateAtEvent(@PathVariable long id, @PathVariable int seq) {
		return log.stateAtEvent(id, seq);
	}

}

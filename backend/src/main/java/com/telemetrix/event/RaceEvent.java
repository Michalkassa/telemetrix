package com.telemetrix.event;

import java.time.Instant;
import java.util.List;

/**
 * Something that happened in a race, for the timeline. `lap` is the leader's lap (the car's own lap for a
 * fastest lap); `endsAt`/`endLap` are set for things that last (a safety car, a pit stop); `participants`
 * are car numbers, the one acting first (the overtaker); `link` is a team radio recording.
 */
public record RaceEvent(EventType type, Instant at, Integer lap, Instant endsAt, Integer endLap, Severity severity,
		List<Integer> participants, String summary, String link) {

	public RaceEvent {
		participants = List.copyOf(participants);
	}

}

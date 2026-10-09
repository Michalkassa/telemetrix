package com.telemetrix.event;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.race.Race;
import com.telemetrix.race.RaceRepository;
import com.telemetrix.race.Session;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TimelineController {

	record Participant(int number, String code) {
	}

	record EventResponse(EventType type, Instant at, long t, Integer lap, Instant endsAt, Integer endLap,
			Severity severity, List<Participant> participants, String summary, String link) {
	}

	record TimelineResponse(long raceId, long sessionId, Instant clockStart, List<EventResponse> events) {
	}

	private final RaceRepository races;

	private final TimelineService timelines;

	TimelineController(RaceRepository races, TimelineService timelines) {
		this.races = races;
		this.timelines = timelines;
	}

	// The race's events in time order (its Race session; open the race first). ?type=SAFETY_CAR&type=VSC
	// keeps only those types. `t` is milliseconds after clockStart, the replay clock's zero.
	@GetMapping("/api/races/{id}/timeline")
	@Transactional(readOnly = true)
	TimelineResponse timeline(@PathVariable long id, @RequestParam(name = "type", required = false) Set<EventType> types) {
		Race race = races.findById(id).orElseThrow(() -> new NotFoundException("Race", id));
		Session session = race.getSessions().stream()
				.filter(s -> s.getName().equals("Race"))
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Race session of race", id));
		Timeline timeline = timelines.timeline(session.getId());
		long zero = timeline.clockStart().toEpochMilli();
		return new TimelineResponse(id, session.getId(), timeline.clockStart(), timeline.events().stream()
				.filter(e -> types == null || types.isEmpty() || types.contains(e.type()))
				.map(e -> new EventResponse(e.type(), e.at(), e.at().toEpochMilli() - zero, e.lap(), e.endsAt(),
						e.endLap(), e.severity(),
						e.participants().stream().map(n -> new Participant(n, timeline.codes().get(n))).toList(),
						e.summary(), e.link()))
				.toList());
	}

}

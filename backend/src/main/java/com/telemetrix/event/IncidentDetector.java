package com.telemetrix.event;

import com.telemetrix.event.RaceData.Message;
import com.telemetrix.racecontrol.RaceControlParser;
import com.telemetrix.racecontrol.RaceControlSignal;
import com.telemetrix.racecontrol.TrackStatus;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

// Incidents and crashes. No source has a "crash" event (docs/adr/0003), so a crash is raised only when
// three signals agree around a collision or "car stopped" race-control message:
// - a double yellow, or the safety car / VSC / red flag, from 3 minutes before to 1 minute after it;
// - one of its cars retiring (by 5 minutes after it) or losing 3 or more places (by 2 minutes after).
// The crash is timed when the flags came out. Every incident the stewards note is an INCIDENT (once,
// however many times it's mentioned) unless it was a crash. A track-limits note is neither.
@Component
class IncidentDetector implements EventDetector {

	private static final Duration FLAGS_BEFORE = Duration.ofMinutes(3);

	private static final Duration FLAGS_AFTER = Duration.ofMinutes(1);

	private static final Duration RETIRED_WITHIN = Duration.ofMinutes(5);

	private static final Duration PLACES_LOST_BY = Duration.ofMinutes(2);

	private static final int MANY_PLACES = 3;

	private static final Set<TrackStatus> NEUTRALISED = Set.of(TrackStatus.SAFETY_CAR, TrackStatus.VSC, TrackStatus.RED);

	@Override
	public List<RaceEvent> detect(RaceData race) {
		List<RaceEvent> events = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Message message : race.raceControl()) {
			switch (RaceControlParser.parse(message.text())) {
				case RaceControlSignal.Incident incident -> {
					String key = incident.cars().stream().sorted().toList() + incident.reason();
					if (!seen.add(key)) {
						continue; // the same incident, investigated or decided on
					}
					String what = names(race, incident.cars()) + " — " + incident.reason().toLowerCase(Locale.ROOT);
					RaceEvent crash = incident.reason().contains("COLLISION")
							? crash(race, message, incident.cars(), what) : null;
					events.add(crash != null ? crash
							: new RaceEvent(EventType.INCIDENT, message.at(), lap(race, message), null, null,
									Severity.MEDIUM, incident.cars(), "Incident: " + what, null));
				}
				case RaceControlSignal.CarStopped stopped -> {
					RaceEvent crash = crash(race, message, List.of(stopped.car()),
							race.code(stopped.car()) + " — stopped on track");
					if (crash != null) {
						events.add(crash);
					}
				}
				default -> {
				}
			}
		}
		return events;
	}

	private static RaceEvent crash(RaceData race, Message message, List<Integer> cars, String what) {
		Instant from = message.at().minus(FLAGS_BEFORE);
		Instant to = message.at().plus(FLAGS_AFTER);
		Instant flags = Stream.concat(
				race.raceControl().stream()
						.filter(m -> m.text() != null && m.text().startsWith("DOUBLE YELLOW"))
						.map(Message::at),
				race.trackStatus().stream()
						.filter(p -> NEUTRALISED.contains(p.status()))
						.map(TrackStatusPeriod::from))
				.filter(at -> !at.isBefore(from) && !at.isAfter(to))
				.min(Instant::compareTo)
				.orElse(null);
		if (flags == null || cars.stream().noneMatch(car -> harmed(race, car, from, message.at()))) {
			return null;
		}
		return new RaceEvent(EventType.CRASH, flags, race.lapAt(flags), null, null, Severity.HIGH, cars,
				"Crash: " + what, null);
	}

	// retired, or lost many places
	private static boolean harmed(RaceData race, int car, Instant from, Instant messageAt) {
		Instant retired = race.retiredAt(car);
		if (retired != null && !retired.isBefore(from) && !retired.isAfter(messageAt.plus(RETIRED_WITHIN))) {
			return true;
		}
		int before = race.positionAt(car, from);
		int after = race.positionAt(car, messageAt.plus(PLACES_LOST_BY));
		return before > 0 && after - before >= MANY_PLACES;
	}

	private static int lap(RaceData race, Message message) {
		return message.lap() != null ? message.lap() : race.lapAt(message.at());
	}

	private static String names(RaceData race, List<Integer> cars) {
		return cars.stream().map(race::code).collect(Collectors.joining(", "));
	}

}

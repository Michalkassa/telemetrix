package com.telemetrix.ingestion;

import com.telemetrix.ingestion.jolpica.JolpicaRace;
import com.telemetrix.ingestion.openf1.OpenF1Session;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Checks source rows before they are stored. Each check returns the reason a row is rejected, or
// empty when it is fine; importers skip rejected rows and report the reason.
@Component
class CalendarValidator {

	// Sessions run from Thursday/Friday to the race on Sunday
	private static final int MAX_DAYS_FROM_RACE = 4;

	Optional<String> checkRace(JolpicaRace race) {
		String what = "race " + race.round() + " " + race.raceName();
		if (race.raceName() == null || race.raceName().isBlank()) {
			return Optional.of(what + " has no name");
		}
		if (race.round() < 1) {
			return Optional.of(what + ": round must be 1 or more");
		}
		if (race.date() == null) {
			return Optional.of(what + " has no date");
		}
		if (race.circuit() == null || race.circuit().circuitId() == null || race.circuit().circuitId().isBlank()) {
			return Optional.of(what + " has no circuit");
		}
		return Optional.empty();
	}

	Optional<String> checkSession(OpenF1Session session, LocalDate raceDay) {
		String what = "session " + session.sessionKey() + " " + session.sessionName();
		if (session.dateStart() == null) {
			return Optional.of(what + " has no start time");
		}
		if (session.dateEnd() != null && !session.dateEnd().isAfter(session.dateStart())) {
			return Optional.of(what + " ends before it starts");
		}
		LocalDate day = session.dateStart().toLocalDate();
		if (Math.abs(ChronoUnit.DAYS.between(day, raceDay)) > MAX_DAYS_FROM_RACE) {
			return Optional.of(what + " on " + day + " is outside the race weekend (race day " + raceDay + ")");
		}
		return Optional.empty();
	}

}

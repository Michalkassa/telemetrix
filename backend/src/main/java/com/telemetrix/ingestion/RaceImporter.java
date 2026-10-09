package com.telemetrix.ingestion;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.ingestion.openf1.OpenF1Session;
import com.telemetrix.race.Race;
import com.telemetrix.race.RaceRepository;
import com.telemetrix.race.Session;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Downloads one race's data, on demand, when a user opens it (docs/adr/0003). Once a race that has
// already happened is imported it is marked, and later calls make no API calls at all. A race still
// to come is imported but not marked, so its sessions are refreshed next time.
// Race and sprint sessions also get their laps, tyre stints and pit stops (SessionTimingImporter), and
// the circuit its track map (CircuitLayoutImporter);
// Step 21 adds race control and weather.
@Service
public class RaceImporter {

	private static final Logger log = LoggerFactory.getLogger(RaceImporter.class);

	// Sessions replayed on the track map: their laps, stints and pit stops are downloaded too.
	private static final Set<String> TIMED_SESSIONS = Set.of("Race", "Sprint");

	private final OpenF1Client openF1;

	private final RaceRepository races;

	private final CalendarValidator validator;

	private final SessionTimingImporter timing;

	private final CircuitLayoutImporter layouts;

	private final Clock clock;

	RaceImporter(OpenF1Client openF1, RaceRepository races, CalendarValidator validator, SessionTimingImporter timing,
			CircuitLayoutImporter layouts, Clock clock) {
		this.openF1 = openF1;
		this.races = races;
		this.validator = validator;
		this.timing = timing;
		this.layouts = layouts;
		this.clock = clock;
	}

	@Transactional
	public ImportReport importRace(long raceId) {
		Race race = races.findById(raceId).orElseThrow(() -> new NotFoundException("Race", raceId));
		var report = ImportReport.builder();
		String what = "Race " + raceId + " (" + race.getSeason().getYear() + " " + race.getName() + ")";
		if (race.getImportedAt() != null) {
			return report.note(what + " already imported at " + race.getImportedAt()).build();
		}
		if (race.getOpenf1MeetingKey() == null) {
			return report.note(what + ": no OpenF1 data (OpenF1 starts in " + OpenF1Client.FIRST_SEASON + ")")
					.build();
		}

		for (OpenF1Session source : openF1.sessions(race.getOpenf1MeetingKey())) {
			if (Boolean.TRUE.equals(source.isCancelled())) {
				report.skipped("sessions", "session " + source.sessionKey() + " " + source.sessionName() + ": cancelled");
				continue;
			}
			Optional<String> problem = validator.checkSession(source, race.getRaceDate());
			if (problem.isPresent()) {
				report.skipped("sessions", problem.get());
				continue;
			}
			upsertSession(race, source, report);
		}

		races.flush(); // new sessions get their ids before timing rows point at them
		Session mapSession = null;
		SectorSegments mapSegments = null;
		for (Session session : race.getSessions()) {
			if (TIMED_SESSIONS.contains(session.getName()) && session.getOpenf1SessionKey() != null) {
				SectorSegments segments = timing.importSession(session, report);
				if (mapSession == null || session.getName().equals("Race")) { // prefer the race's laps
					mapSession = session;
					mapSegments = segments;
				}
			}
		}
		if (mapSession != null) {
			layouts.ensureLayout(race, mapSession, mapSegments, report);
		}

		if (race.getRaceDate().isBefore(LocalDate.now(clock))) {
			race.markImported(clock.instant());
		}
		ImportReport result = report.build();
		log.info("Imported {}: {}", what, result.counts());
		return result;
	}

	private void upsertSession(Race race, OpenF1Session source, ImportReport.Builder report) {
		var startsAt = source.dateStart().toInstant();
		var endsAt = source.dateEnd() != null ? source.dateEnd().toInstant() : null;
		Optional<Session> existing = race.getSessions().stream()
				.filter(s -> Objects.equals(s.getOpenf1SessionKey(), source.sessionKey()))
				.findFirst();
		if (existing.isPresent()) {
			report.changed("sessions", existing.get().update(source.sessionName(), startsAt, endsAt));
			return;
		}
		var session = new Session(source.sessionName(), startsAt);
		session.setEndsAt(endsAt);
		session.setOpenf1SessionKey(source.sessionKey());
		race.addSession(session);
		report.inserted("sessions");
	}

}

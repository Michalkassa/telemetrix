package com.telemetrix.ingestion;

import com.telemetrix.circuit.Circuit;
import com.telemetrix.circuit.CircuitRepository;
import com.telemetrix.ingestion.jolpica.JolpicaClient;
import com.telemetrix.ingestion.jolpica.JolpicaRace;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.ingestion.openf1.OpenF1Meeting;
import com.telemetrix.race.Race;
import com.telemetrix.race.RaceRepository;
import com.telemetrix.race.Season;
import com.telemetrix.race.SeasonRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Imports a season's calendar (docs/adr/0003): Jolpica owns seasons, circuits and races with their
// official rounds; for 2023+ each race is linked to its OpenF1 meeting by date. 2 requests per
// season. Rows are upserted by their source keys, so importing again is safe.
// Race data itself (sessions, laps, ...) is downloaded per race, on demand: RaceImporter.
@Service
public class CalendarImporter {

	private static final Logger log = LoggerFactory.getLogger(CalendarImporter.class);

	private final JolpicaClient jolpica;

	private final OpenF1Client openF1;

	private final SeasonRepository seasons;

	private final CircuitRepository circuits;

	private final RaceRepository races;

	private final CalendarValidator validator;

	CalendarImporter(JolpicaClient jolpica, OpenF1Client openF1, SeasonRepository seasons, CircuitRepository circuits,
			RaceRepository races, CalendarValidator validator) {
		this.jolpica = jolpica;
		this.openF1 = openF1;
		this.seasons = seasons;
		this.circuits = circuits;
		this.races = races;
		this.validator = validator;
	}

	@Transactional
	public ImportReport importSeason(int year) {
		var report = ImportReport.builder();
		Season season = seasons.findById((short) year).orElseGet(() -> {
			report.inserted("seasons");
			return seasons.save(new Season((short) year));
		});

		List<Race> seasonRaces = new ArrayList<>();
		for (JolpicaRace source : jolpica.races(year)) {
			Optional<String> problem = validator.checkRace(source);
			if (problem.isPresent()) {
				report.skipped("races", problem.get());
				continue;
			}
			seasonRaces.add(upsertRace(season, source, upsertCircuit(source.circuit(), report), report));
		}

		if (year >= OpenF1Client.FIRST_SEASON) {
			for (OpenF1Meeting meeting : openF1.meetings(year)) {
				linkMeeting(meeting, seasonRaces, report);
			}
		}

		ImportReport result = report.build();
		log.info("Imported the {} calendar: {}", year, result.counts());
		return result;
	}

	private Circuit upsertCircuit(JolpicaRace.Circuit source, ImportReport.Builder report) {
		var location = source.location() != null ? source.location() : new JolpicaRace.Location(null, null, null, null);
		Optional<Circuit> existing = circuits.findByRef(source.circuitId());
		if (existing.isPresent()) {
			Circuit circuit = existing.get();
			report.changed("circuits", circuit.update(source.circuitName(), location.locality(), location.country(),
					location.lat(), location.lng()));
			return circuit;
		}
		Circuit circuit = new Circuit(source.circuitId(), source.circuitName());
		circuit.update(source.circuitName(), location.locality(), location.country(), location.lat(), location.lng());
		report.inserted("circuits");
		return circuits.save(circuit);
	}

	private Race upsertRace(Season season, JolpicaRace source, Circuit circuit, ImportReport.Builder report) {
		Optional<Race> existing = races.findBySeasonYearAndRound(season.getYear(), (short) source.round());
		if (existing.isPresent()) {
			Race race = existing.get();
			report.changed("races", race.update(source.raceName(), source.date(), circuit));
			return race;
		}
		report.inserted("races");
		return races.save(new Race(season, (short) source.round(), circuit, source.raceName(), source.date()));
	}

	// An OpenF1 meeting spans the weekend; the race it belongs to is the one whose date falls inside.
	private void linkMeeting(OpenF1Meeting meeting, List<Race> seasonRaces, ImportReport.Builder report) {
		String what = "meeting " + meeting.meetingKey() + " " + meeting.meetingName();
		if (Boolean.TRUE.equals(meeting.isCancelled())) {
			report.skipped("meetings", what + ": cancelled");
			return;
		}
		if (meeting.dateStart() == null || meeting.dateEnd() == null) {
			report.skipped("meetings", what + ": no dates");
			return;
		}
		var from = meeting.dateStart().toLocalDate();
		var to = meeting.dateEnd().toLocalDate();
		Optional<Race> race = seasonRaces.stream()
				.filter(r -> !r.getRaceDate().isBefore(from) && !r.getRaceDate().isAfter(to))
				.findFirst();
		if (race.isEmpty()) {
			report.skipped("meetings", what + ": no race on its dates " + from + " to " + to);
			return;
		}
		boolean changed = race.get().linkOpenF1Meeting(meeting.meetingKey());
		changed |= race.get().getCircuit().linkOpenF1Circuit(meeting.circuitKey());
		report.changed("meetings", changed);
	}

}

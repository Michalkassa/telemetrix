package com.telemetrix.race;

import com.telemetrix.circuit.Circuit;
import com.telemetrix.common.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reads seasons and races for the API and turns them into response records.
@Service
@Transactional(readOnly = true)
public class RaceQueryService {

	private final SeasonRepository seasons;

	private final RaceRepository races;

	RaceQueryService(SeasonRepository seasons, RaceRepository races) {
		this.seasons = seasons;
		this.races = races;
	}

	public List<RaceDtos.SeasonResponse> seasons() {
		return seasons.findAllWithRaceCounts().stream()
				.map(s -> new RaceDtos.SeasonResponse(s.year(), s.raceCount()))
				.toList();
	}

	public RaceDtos.SeasonRacesResponse racesOf(int year) {
		if (!seasons.existsById((short) year)) {
			throw new NotFoundException("Season", year);
		}
		var summaries = races.findAllBySeasonYearOrderByRound((short) year).stream()
				.map(race -> new RaceDtos.RaceSummary(id(race), race.getRound(), race.getName(), race.getRaceDate(),
						circuit(race.getCircuit()), race.getOpenf1MeetingKey() != null))
				.toList();
		return new RaceDtos.SeasonRacesResponse(year, summaries);
	}

	public RaceDtos.RaceDetail race(long id) {
		Race race = races.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Race", id));
		var sessions = race.getSessions().stream()
				.map(s -> new RaceDtos.SessionResponse(s.getId() == null ? 0 : s.getId(), s.getName(), s.getStartsAt(),
						s.getEndsAt()))
				.toList();
		return new RaceDtos.RaceDetail(id(race), race.getSeason().getYear(), race.getRound(), race.getName(),
				race.getRaceDate(), circuit(race.getCircuit()), race.getOpenf1MeetingKey(), race.getImportedAt(),
				sessions);
	}

	private static RaceDtos.CircuitResponse circuit(Circuit circuit) {
		return new RaceDtos.CircuitResponse(circuit.getRef(), circuit.getName(), circuit.getLocality(),
				circuit.getCountry());
	}

	// unsaved entities (unit tests) have no id yet
	private static long id(Race race) {
		return race.getId() == null ? 0 : race.getId();
	}

}

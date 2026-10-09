package com.telemetrix.race;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// The race API's JSON shapes (docs/api.md). Entities never leave the service.
public final class RaceDtos {

	private RaceDtos() {
	}

	public record SeasonResponse(int year, long raceCount) {
	}

	public record CircuitResponse(String ref, String name, String locality, String country) {
	}

	public record RaceSummary(long id, int round, String name, LocalDate date, CircuitResponse circuit,
			boolean hasOpenF1Data) {
	}

	public record SeasonRacesResponse(int year, List<RaceSummary> races) {
	}

	public record SessionResponse(long id, String name, Instant startsAt, Instant endsAt) {
	}

	public record RaceDetail(long id, int season, int round, String name, LocalDate date, CircuitResponse circuit,
			Integer openf1MeetingKey, Instant importedAt, List<SessionResponse> sessions) {
	}

}

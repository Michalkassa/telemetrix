package com.telemetrix.result;

import java.util.List;

/** GET /api/races/{id}/results: the race's classification, in finishing order. */
public record RaceResults(long raceId, List<Row> results) {

	/** `code` is null for many drivers before the 2000s; `timeMs` only for the winner and cars on the lead lap. */
	public record Row(Integer position, String code, String givenName, String surname, String team, Integer number,
			Integer grid, Integer laps, Long timeMs, String status, String statusDetail, Boolean classified,
			Double points) {
	}

}

package com.telemetrix.championship;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/sessions/{id}/replay/championship?at=: the drivers' championship "if they finished now" —
 * the standings before this race plus the points the running order at `at` would earn.
 */
public record LiveStandings(int season, int round, Instant at, List<Driver> drivers) {

	/** `positionBefore` null: no points before this race. */
	public record Driver(int position, Integer positionBefore, int number, String ref, String code, String surname,
			double before, double race, double total) {
	}

}

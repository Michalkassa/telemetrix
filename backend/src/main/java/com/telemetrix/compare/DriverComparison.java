package com.telemetrix.compare;

import java.util.List;

/** Two drivers in the Grand Prix of a season that both entered, race by race. */
public record DriverComparison(int season, Driver a, Driver b, Tally tally, List<Race> races) {

	public record Driver(String ref, String code, String givenName, String surname) {
	}

	/** Who finished ahead (a retirement is behind a finisher) and started ahead, and their points. */
	public record Tally(int races, int aAheadInRace, int bAheadInRace, int aAheadOnGrid, int bAheadOnGrid,
			double aPoints, double bPoints) {
	}

	/** `medianGapMs` is a's median clean lap minus b's: negative when a was quicker. */
	public record Race(int round, long raceId, String name, Result a, Result b, Long medianGapMs) {
	}

	/** A driver's race; the median is over clean laps (not lap 1, not into or out of the pits, within 107%). */
	public record Result(String team, Integer grid, Integer position, boolean classified, String status,
			String statusDetail, double points, Long medianLapMs, Long bestLapMs) {
	}

}

package com.telemetrix.analytics;

/** A driver in the directory: the seasons they raced in, their starts and wins. */
public record DriverSummary(String ref, String code, String givenName, String surname, String nationality,
		int firstSeason, int lastSeason, int starts, int wins) {
}

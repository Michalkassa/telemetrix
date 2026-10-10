package com.telemetrix.analytics;

/** One Grand Prix in a driver's race log; `position` is the classification order, even for a retirement. */
public record RaceLogEntry(int season, int round, long raceId, String race, String teamRef, String team,
		Integer grid, Integer position, boolean classified, String status, String statusDetail, double points) {
}

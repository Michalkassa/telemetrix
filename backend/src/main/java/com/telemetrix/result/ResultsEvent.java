package com.telemetrix.result;

/**
 * One event in a race's results stream (docs/adr/0006): it opens with the race's conditions, then a line
 * per car per Grand Prix ('R') and sprint ('SR'), as the history import left them.
 */
public sealed interface ResultsEvent {

	/** Opens the stream. `wet`: rain during the race (OpenF1 weather, 2023 on); null when unknown. */
	record ResultsRecorded(Boolean wet) implements ResultsEvent {
	}

	/** One car's result; refs are drivers.ref / constructors.ref, ids those of race_entries, drivers, constructors. */
	record ResultRecorded(String sessionType, int sessionNumber, long raceEntryId, long driverId, String driverRef,
			long constructorId, String constructorRef, Integer carNumber, Integer grid, Integer position,
			Boolean classified, String status, Double points, Integer fastestLapRank) implements ResultsEvent {
	}

}

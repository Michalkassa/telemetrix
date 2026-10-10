package com.telemetrix.result;

import java.util.List;

/**
 * GET /api/races/{id}/lap-times: every car's race lap times, in finishing order. `source`: OPENF1 (the
 * race opened, 2023 on), JOLPICA (the history, 1996 on), or NONE.
 */
public record LapTimes(long raceId, Source source, List<Car> drivers) {

	public enum Source {
		OPENF1, JOLPICA, NONE
	}

	public record Car(int number, String code, String surname, Integer position, List<Lap> laps) {
	}

	public record Lap(int lap, long ms) {
	}

}

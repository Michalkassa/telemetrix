package com.telemetrix.state;

import java.time.Instant;
import java.util.List;

/** The whole race at a moment: the leader's lap, the flag, and every car in race order (retired last). */
public record RaceState(Instant at, int lap, Flag flag, List<CarState> cars) {

	public RaceState {
		cars = List.copyOf(cars);
	}

}

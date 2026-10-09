package com.telemetrix.state;

import java.util.Map;

/**
 * The part of the race state that builds up from events (RaceStateEngine folds them): the flag, and per
 * car its tyres, pit stops and penalties. Positions and gaps aren't here: they come straight from the
 * timing at any moment.
 */
public record DerivedState(Flag flag, Map<Integer, Car> cars) {

	/** `stintLapStart`/`ageAtStart`: the lap the current tyres were fitted for and their age then. */
	public record Car(String compound, Integer stintLapStart, int ageAtStart, int pitStops, boolean inPit,
			int penaltySeconds, int pendingPenalties) {

		static final Car NONE = new Car(null, null, 0, 0, false, 0, 0);

	}

	public DerivedState {
		cars = Map.copyOf(cars);
	}

	public Car car(int number) {
		return cars.getOrDefault(number, Car.NONE);
	}

}

package com.telemetrix.state;

import java.util.Map;

/**
 * The part of the race state that builds up from events (RaceStateEngine folds them): the flag, the
 * latest weather (null before the first reading), and per car its tyres, pit stops and penalties.
 * Positions and gaps aren't here: they come straight from the timing at any moment.
 */
public record DerivedState(Flag flag, Map<Integer, SectorFlag> yellowSectors, boolean drsEnabled, WeatherNow weather,
		Map<Integer, Car> cars) {

	/**
	 * `stintLapStart`/`ageAtStart`: the lap the current tyres were fitted for and their age then;
	 * `compounds`: every compound used so far (for the two-compound rule).
	 */
	public record Car(String compound, Integer stintLapStart, int ageAtStart, int pitStops, boolean inPit,
			int penaltySeconds, int pendingPenalties, java.util.Set<String> compounds) {

		static final Car NONE = new Car(null, null, 0, 0, false, 0, 0, java.util.Set.of());

		public Car {
			compounds = java.util.Set.copyOf(compounds);
		}

		Car withPit(int pitStops, boolean inPit, int pendingPenalties) {
			return new Car(compound, stintLapStart, ageAtStart, pitStops, inPit, penaltySeconds, pendingPenalties,
					compounds);
		}

		Car withTyres(String newCompound, int lapStart, int age) {
			var used = new java.util.HashSet<>(compounds);
			if (newCompound != null) {
				used.add(newCompound);
			}
			return new Car(newCompound, lapStart, age, pitStops, inPit, penaltySeconds, pendingPenalties, used);
		}

		Car withPenalties(int seconds, int pending) {
			return new Car(compound, stintLapStart, ageAtStart, pitStops, inPit, seconds, pending, compounds);
		}

		/** The rule for a dry race: two different dry compounds. A car that used wet tyres is exempt. */
		boolean mandatoryStopDone() {
			if (compounds.contains("INTERMEDIATE") || compounds.contains("WET")) {
				return true;
			}
			return compounds.stream().filter(c -> c.equals("SOFT") || c.equals("MEDIUM") || c.equals("HARD")).count() >= 2;
		}

	}

	public DerivedState {
		yellowSectors = Map.copyOf(yellowSectors);
		cars = Map.copyOf(cars);
	}

	public Car car(int number) {
		return cars.getOrDefault(number, Car.NONE);
	}

}

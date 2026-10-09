package com.telemetrix.replay;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// One moment of a race: every car's position, lap, progress round the lap and place on the map.
// Built with PlaybackFrame.at(...).car(...).car(...).build(), which orders the cars.
public record PlaybackFrame(Instant at, int leaderLap, List<Car> cars) {

	public enum Status {
		ON_GRID, RUNNING, IN_PIT, FINISHED, RETIRED
	}

	/** What the replay knows about a car at this moment, before the cars are ordered. */
	public record CarState(int number, double lapsDone, Instant reachedAt, Status status, double x, double y) {
	}

	/** `lap` is the lap the car is on (a finished car: the laps it completed). */
	public record Car(int number, int position, int lap, double progress, double lapsDone, Status status, double x,
			double y) {
	}

	public static Builder at(Instant at, int totalLaps) {
		return new Builder(at, totalLaps);
	}

	public static final class Builder {

		// further round the race first; level cars (stopped at the same distance) by who got there first
		private static final Comparator<CarState> RACE_ORDER = Comparator
				.comparingDouble(CarState::lapsDone).reversed()
				.thenComparing(CarState::reachedAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparingInt(CarState::number);

		private final Instant at;

		private final int totalLaps;

		private final List<CarState> cars = new ArrayList<>();

		private Builder(Instant at, int totalLaps) {
			this.at = at;
			this.totalLaps = totalLaps;
		}

		public Builder car(CarState car) {
			cars.add(car);
			return this;
		}

		public PlaybackFrame build() {
			List<CarState> ordered = cars.stream().sorted(RACE_ORDER).toList();
			List<Car> result = new ArrayList<>(ordered.size());
			for (int i = 0; i < ordered.size(); i++) {
				CarState c = ordered.get(i);
				int whole = (int) Math.floor(c.lapsDone());
				int lap = c.status() == Status.FINISHED ? whole : whole + 1;
				if (totalLaps > 0) {
					lap = Math.min(lap, totalLaps);
				}
				result.add(new Car(c.number(), i + 1, Math.max(lap, 1), c.lapsDone() - whole, c.lapsDone(), c.status(),
						c.x(), c.y()));
			}
			return new PlaybackFrame(at, result.isEmpty() ? 0 : result.getFirst().lap(), List.copyOf(result));
		}

	}

}

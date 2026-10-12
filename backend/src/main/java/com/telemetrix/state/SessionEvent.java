package com.telemetrix.state;

import com.telemetrix.event.RaceEvent;
import com.telemetrix.racecontrol.PenaltyKind;
import java.time.Instant;
import java.util.Map;

/**
 * One event in a race session's stream (docs/adr/0006), the source of truth for its replay: the stream
 * opens with SessionRecorded, then everything that changes the race state, and the timeline's detected
 * events (Detected), in the order they happened. `at` is null for what holds from before the start (the
 * stream's opening, the tyres each car starts on).
 */
public sealed interface SessionEvent {

	Instant at();

	/** Opens the stream: the replay clock's zero, the chequered flag, the race distance, car -> "VER". */
	record SessionRecorded(Instant clockStart, Instant chequeredAt, int totalLaps, Map<Integer, String> codes)
			implements SessionEvent {

		public SessionRecorded {
			codes = Map.copyOf(codes);
		}

		@Override
		public Instant at() {
			return null;
		}

	}

	record FlagChanged(Instant at, Flag flag) implements SessionEvent {
	}

	record PitIn(Instant at, int car) implements SessionEvent {
	}

	record PitOut(Instant at, int car) implements SessionEvent {
	}

	/** `at` null: the set the car starts on. `ageAtStart`: laps already on them. */
	record TyresFitted(Instant at, int car, String compound, int lapStart, int ageAtStart) implements SessionEvent {
	}

	record PenaltyGiven(Instant at, int car, PenaltyKind kind, Integer amount) implements SessionEvent {
	}

	/** `flag` null: the sector is clear. */
	record SectorFlagged(Instant at, int sector, SectorFlag flag) implements SessionEvent {
	}

	record TrackCleared(Instant at) implements SessionEvent {
	}

	record DrsChanged(Instant at, boolean enabled) implements SessionEvent {
	}

	/** °C, %, m/s; null when not measured. */
	record WeatherReported(Instant at, Double airTemperature, Double trackTemperature, Double humidity,
			boolean rainfall, Double windSpeed) implements SessionEvent {
	}

	/** A timeline event a detector found; it doesn't change the state. */
	record Detected(RaceEvent event) implements SessionEvent {

		@Override
		public Instant at() {
			return event.at();
		}

	}

}

package com.telemetrix.racecontrol;

import java.util.List;

// What a race-control message means (RaceControlParser).
public sealed interface RaceControlSignal {

	/** A yellow (or double yellow) in one marshalling sector, or that sector cleared. */
	record SectorFlag(int sector, boolean yellow) implements RaceControlSignal {
	}

	/** The whole track is clear: every yellow, the safety car and the VSC are over. */
	record TrackClear() implements RaceControlSignal {
	}

	record StatusChange(TrackStatus status) implements RaceControlSignal {
	}

	/** The safety car comes in at the end of this lap; it's still out until the track is clear. */
	record SafetyCarInThisLap() implements RaceControlSignal {
	}

	record Chequered() implements RaceControlSignal {
	}

	/** `amount`: seconds (TIME, STOP_GO) or grid places (GRID); null for a drive-through. */
	record Penalty(int driverNumber, PenaltyKind kind, Integer amount, String reason) implements RaceControlSignal {
	}

	/** An incident the stewards have noted, are investigating, or have decided on. */
	record Incident(List<Integer> cars, String decision, String reason) implements RaceControlSignal {
	}

	/** A car stopped on track. */
	record CarStopped(int car) implements RaceControlSignal {
	}

	record Other() implements RaceControlSignal {
	}

}

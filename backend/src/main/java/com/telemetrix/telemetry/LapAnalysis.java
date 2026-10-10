package com.telemetrix.telemetry;

import java.time.Instant;
import java.util.List;

/**
 * One lap of one car for the telemetry view: its time and sector times (seconds), tyres, estimates, and
 * the trace against distance (`distance` m, `time` ms since the lap started).
 */
public record LapAnalysis(long sessionId, int driver, int lap, Instant startsAt, double lapTime, Double[] sectors,
		Tyre tyre, EstimatesResponse estimates, double distance, List<LapTrace.Point> trace) {

	/** `age`: laps on the tyres at this lap, counting any done before they were fitted. */
	public record Tyre(String compound, int stint, int age) {
	}

	public record EstimatesResponse(double fuelKg, double ersDeployedMj, double ersHarvestedMj, String note) {
	}

	/** Two laps compared: `lapDelta` and `sectorDeltas` are other − reference, in seconds. */
	public record Comparison(LapAnalysis reference, LapAnalysis other, double lapDelta, Double[] sectorDeltas,
			List<LapTrace.DeltaPoint> delta) {
	}

}

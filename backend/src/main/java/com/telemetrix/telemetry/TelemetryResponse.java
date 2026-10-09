package com.telemetrix.telemetry;

import java.time.Instant;
import java.util.List;

// GET /api/sessions/{id}/telemetry: one car's samples between `from` and `to` (a lap, or the session).
public record TelemetryResponse(long sessionId, int driver, Integer lap, Instant from, Instant to, List<Point> points,
		List<TelemetryGap> gaps) {

	public record Point(Instant t, Integer speed, Integer throttle, Integer brake, Integer gear, Integer rpm,
			Integer drs, Integer x, Integer y, Integer z) {
	}

}

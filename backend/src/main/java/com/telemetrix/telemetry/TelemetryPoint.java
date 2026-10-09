package com.telemetrix.telemetry;

import java.time.Instant;

// One merged sample of one car: car data plus its position (x, y, z null when unknown).
public record TelemetryPoint(Instant recordedAt, Integer speed, Integer throttle, Integer brake, Integer gear,
		Integer rpm, Integer drs, Integer x, Integer y, Integer z) {
}

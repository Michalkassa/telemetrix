package com.telemetrix.telemetry;

import java.time.Instant;

// A stretch with no samples: between two samples more than 2 s apart.
public record TelemetryGap(Instant from, Instant to) {
}

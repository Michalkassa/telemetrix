package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// One position sample (~3.7 Hz), as OpenF1 returns it from /location. (0, 0, 0) means "no position".
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Location(
		@JsonProperty("driver_number") int driverNumber,
		@JsonProperty("date") OffsetDateTime date,
		@JsonProperty("x") Integer x,
		@JsonProperty("y") Integer y,
		@JsonProperty("z") Integer z) {
}

package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// One race-control message, as OpenF1 returns it from /race_control: flags, safety car, penalties,
// incidents. `scope` is Track, Sector or Driver; `sector` is the marshalling sector for a sector flag.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1RaceControl(
		@JsonProperty("date") OffsetDateTime date,
		@JsonProperty("lap_number") Integer lapNumber,
		@JsonProperty("category") String category,
		@JsonProperty("flag") String flag,
		@JsonProperty("scope") String scope,
		@JsonProperty("sector") Integer sector,
		@JsonProperty("driver_number") Integer driverNumber,
		@JsonProperty("message") String message) {
}

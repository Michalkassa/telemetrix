package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// One set of tyres on one car, as OpenF1 returns it from /stints.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Stint(
		@JsonProperty("driver_number") int driverNumber,
		@JsonProperty("stint_number") int stintNumber,
		@JsonProperty("compound") String compound,
		@JsonProperty("lap_start") Integer lapStart,
		@JsonProperty("lap_end") Integer lapEnd,
		@JsonProperty("tyre_age_at_start") Integer tyreAgeAtStart) {
}

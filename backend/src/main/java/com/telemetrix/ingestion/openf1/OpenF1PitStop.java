package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// One pit stop, as OpenF1 returns it from /pit. Seconds. Older data has only pit_duration (the time
// in the pit lane); newer data splits it into lane_duration and stop_duration (stationary).
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1PitStop(
		@JsonProperty("driver_number") int driverNumber,
		@JsonProperty("lap_number") Integer lapNumber,
		@JsonProperty("date") OffsetDateTime date,
		@JsonProperty("lane_duration") Double laneDuration,
		@JsonProperty("stop_duration") Double stopDuration,
		@JsonProperty("pit_duration") Double pitDuration) {

	/** Time in the pit lane, from whichever field this data has. */
	public Double timeInLane() {
		return laneDuration != null ? laneDuration : pitDuration;
	}

}

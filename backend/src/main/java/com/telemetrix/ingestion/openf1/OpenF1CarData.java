package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// One car-data sample (~3.7 Hz), as OpenF1 returns it from /car_data.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1CarData(
		@JsonProperty("driver_number") int driverNumber,
		@JsonProperty("date") OffsetDateTime date,
		@JsonProperty("speed") Integer speed,
		@JsonProperty("throttle") Integer throttle,
		@JsonProperty("brake") Integer brake,
		@JsonProperty("n_gear") Integer gear,
		@JsonProperty("rpm") Integer rpm,
		@JsonProperty("drs") Integer drs) {
}

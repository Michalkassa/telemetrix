package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// One weather reading at the track (OpenF1 /weather, about once a minute). °C, %, mbar, m/s,
// degrees; rainfall is 0 or 1.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Weather(
		@JsonProperty("date") OffsetDateTime date,
		@JsonProperty("air_temperature") Double airTemperature,
		@JsonProperty("track_temperature") Double trackTemperature,
		@JsonProperty("humidity") Double humidity,
		@JsonProperty("pressure") Double pressure,
		@JsonProperty("rainfall") Integer rainfall,
		@JsonProperty("wind_speed") Double windSpeed,
		@JsonProperty("wind_direction") Integer windDirection) {
}

package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// A Grand Prix weekend (or testing) as OpenF1 returns it from /meetings.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Meeting(
		@JsonProperty("meeting_key") int meetingKey,
		@JsonProperty("meeting_name") String meetingName,
		@JsonProperty("meeting_official_name") String meetingOfficialName,
		@JsonProperty("location") String location,
		@JsonProperty("country_code") String countryCode,
		@JsonProperty("country_name") String countryName,
		@JsonProperty("circuit_key") Integer circuitKey,
		@JsonProperty("circuit_short_name") String circuitShortName,
		@JsonProperty("date_start") OffsetDateTime dateStart,
		@JsonProperty("date_end") OffsetDateTime dateEnd,
		@JsonProperty("year") int year,
		@JsonProperty("is_cancelled") Boolean isCancelled) {
}

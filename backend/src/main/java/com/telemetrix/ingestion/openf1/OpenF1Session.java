package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// A session of a meeting as OpenF1 returns it from /sessions.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Session(
		@JsonProperty("session_key") int sessionKey,
		@JsonProperty("meeting_key") int meetingKey,
		@JsonProperty("session_name") String sessionName,
		@JsonProperty("session_type") String sessionType,
		@JsonProperty("circuit_key") Integer circuitKey,
		@JsonProperty("date_start") OffsetDateTime dateStart,
		@JsonProperty("date_end") OffsetDateTime dateEnd,
		@JsonProperty("year") int year,
		@JsonProperty("is_cancelled") Boolean isCancelled) {
}

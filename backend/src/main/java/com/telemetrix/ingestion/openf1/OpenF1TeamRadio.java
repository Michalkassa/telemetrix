package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// One team radio clip (OpenF1 /team_radio): who, when, and the URL of the recording (an mp3 on F1's
// archive; we store the link, not the audio).
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1TeamRadio(
		@JsonProperty("date") OffsetDateTime date,
		@JsonProperty("driver_number") int driverNumber,
		@JsonProperty("recording_url") String recordingUrl) {
}

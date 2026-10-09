package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// A driver entered in a session, as OpenF1 returns it from /drivers. Any field can be null in
// older sessions (e.g. headshot_url).
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Driver(
		@JsonProperty("driver_number") int driverNumber,
		@JsonProperty("session_key") int sessionKey,
		@JsonProperty("meeting_key") int meetingKey,
		@JsonProperty("broadcast_name") String broadcastName,
		@JsonProperty("full_name") String fullName,
		@JsonProperty("name_acronym") String nameAcronym,
		@JsonProperty("team_name") String teamName,
		@JsonProperty("team_colour") String teamColour,
		@JsonProperty("first_name") String firstName,
		@JsonProperty("last_name") String lastName,
		@JsonProperty("headshot_url") String headshotUrl,
		@JsonProperty("country_code") String countryCode) {
}

package com.telemetrix.ingestion.jolpica;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

// One race of a season's calendar, as Jolpica returns it from /{year}/races.json.
// Jolpica sends numbers as strings; Jackson converts them.
@JsonIgnoreProperties(ignoreUnknown = true)
public record JolpicaRace(
		@JsonProperty("season") int season,
		@JsonProperty("round") int round,
		@JsonProperty("raceName") String raceName,
		@JsonProperty("Circuit") Circuit circuit,
		@JsonProperty("date") LocalDate date) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Circuit(
			@JsonProperty("circuitId") String circuitId,
			@JsonProperty("circuitName") String circuitName,
			@JsonProperty("Location") Location location) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Location(
			@JsonProperty("lat") BigDecimal lat,
			@JsonProperty("long") BigDecimal lng,
			@JsonProperty("locality") String locality,
			@JsonProperty("country") String country) {
	}

}

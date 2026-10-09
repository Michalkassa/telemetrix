package com.telemetrix.ingestion.jolpica;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.telemetrix.ingestion.RateLimitedClient;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;

// Talks to the Jolpica-F1 API (https://github.com/jolpica/jolpica-f1), the Ergast-compatible source
// of the calendar and history. Rate limiting and retries are RateLimitedClient's job.
public class JolpicaClient {

	private final RateLimitedClient http;

	public JolpicaClient(RateLimitedClient http) {
		this.http = http;
	}

	/** GET /{year}/races.json — a season's calendar with circuits, in round order. */
	public List<JolpicaRace> races(int year) {
		return http.get("/" + year + "/races.json", Map.of("limit", 100),
				new ParameterizedTypeReference<RacesResponse>() {
				})
				.map(RacesResponse::races)
				.orElse(List.of());
	}

	// Jolpica wraps every answer: {"MRData": {"RaceTable": {"Races": [...]}}}
	@JsonIgnoreProperties(ignoreUnknown = true)
	record RacesResponse(@JsonProperty("MRData") MrData mrData) {

		List<JolpicaRace> races() {
			if (mrData == null || mrData.raceTable() == null || mrData.raceTable().races() == null) {
				return List.of();
			}
			return mrData.raceTable().races();
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record MrData(@JsonProperty("RaceTable") RaceTable raceTable) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RaceTable(@JsonProperty("Races") List<JolpicaRace> races) {
	}

}

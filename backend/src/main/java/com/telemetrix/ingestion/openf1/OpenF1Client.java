package com.telemetrix.ingestion.openf1;

import com.telemetrix.ingestion.RateLimitedClient;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;

// Talks to the OpenF1 API (https://openf1.org). Callers say WHAT they want and get plain records;
// rate limiting and retries are RateLimitedClient's job. OpenF1 answers "no matching data" with a
// 404, which becomes an empty list.
public class OpenF1Client {

	/** OpenF1 has data from this season on. */
	public static final int FIRST_SEASON = 2023;

	private final RateLimitedClient http;

	public OpenF1Client(RateLimitedClient http) {
		this.http = http;
	}

	/** GET /meetings?year= — every meeting (Grand Prix weekend or test) of a season. */
	public List<OpenF1Meeting> meetings(int year) {
		return http.get("/meetings", Map.of("year", year), new ParameterizedTypeReference<List<OpenF1Meeting>>() {
		}).orElse(List.of());
	}

	/** GET /sessions?meeting_key= — the sessions of one meeting. */
	public List<OpenF1Session> sessions(int meetingKey) {
		return http.get("/sessions", Map.of("meeting_key", meetingKey),
				new ParameterizedTypeReference<List<OpenF1Session>>() {
				}).orElse(List.of());
	}

	/** GET /laps?session_key= — every lap of every car in one session (one request). */
	public List<OpenF1Lap> laps(int sessionKey) {
		return http.get("/laps", Map.of("session_key", sessionKey), new ParameterizedTypeReference<List<OpenF1Lap>>() {
		}).orElse(List.of());
	}

	/** GET /stints?session_key= — every car's tyre stints in one session. */
	public List<OpenF1Stint> stints(int sessionKey) {
		return http.get("/stints", Map.of("session_key", sessionKey),
				new ParameterizedTypeReference<List<OpenF1Stint>>() {
				}).orElse(List.of());
	}

	/** GET /pit?session_key= — every pit stop in one session. */
	public List<OpenF1PitStop> pitStops(int sessionKey) {
		return http.get("/pit", Map.of("session_key", sessionKey), new ParameterizedTypeReference<List<OpenF1PitStop>>() {
		}).orElse(List.of());
	}

	/** GET /drivers?session_key= — the drivers entered in one session. */
	public List<OpenF1Driver> drivers(int sessionKey) {
		return http.get("/drivers", Map.of("session_key", sessionKey),
				new ParameterizedTypeReference<List<OpenF1Driver>>() {
				}).orElse(List.of());
	}

}

package com.telemetrix.ingestion.multiviewer;

import com.telemetrix.ingestion.RateLimitedClient;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;

// Track maps from the (unofficial, free) Multiviewer circuits API — one request per circuit and year,
// stored for good (docs/adr/0003).
public class MultiviewerClient {

	private final RateLimitedClient http;

	public MultiviewerClient(RateLimitedClient http) {
		this.http = http;
	}

	/** GET /circuits/{OpenF1 circuit key}/{year}; empty if there's no map. */
	public Optional<MultiviewerCircuit> circuit(int openF1CircuitKey, int year) {
		return http.get("/circuits/" + openF1CircuitKey + "/" + year, Map.of(),
				new ParameterizedTypeReference<MultiviewerCircuit>() {
				});
	}

}

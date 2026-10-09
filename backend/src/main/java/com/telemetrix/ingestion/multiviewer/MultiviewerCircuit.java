package com.telemetrix.ingestion.multiviewer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// A circuit's track map, as the Multiviewer circuits API returns it (linked from OpenF1's
// circuit_info_url): an outline from one recorded lap, starting at the line, in OpenF1 coordinates.
@JsonIgnoreProperties(ignoreUnknown = true)
public record MultiviewerCircuit(
		@JsonProperty("circuitKey") Integer circuitKey,
		@JsonProperty("x") List<Integer> x,
		@JsonProperty("y") List<Integer> y,
		@JsonProperty("rotation") Double rotation,
		@JsonProperty("corners") List<Marker> corners,
		// the outline index where each mini-sector ends
		@JsonProperty("miniSectorsIndexes") List<Integer> miniSectorsIndexes) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Marker(@JsonProperty("number") Integer number,
			@JsonProperty("trackPosition") TrackPosition trackPosition) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TrackPosition(@JsonProperty("x") double x, @JsonProperty("y") double y) {
	}

}

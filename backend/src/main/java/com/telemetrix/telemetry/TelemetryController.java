package com.telemetrix.telemetry;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TelemetryController {

	private final TelemetryService service;

	private final LapAnalysisService analysis;

	TelemetryController(TelemetryService service, LapAnalysisService analysis) {
		this.service = service;
		this.analysis = analysis;
	}

	// The first request for a car in a session downloads its telemetry (a few seconds); later ones are instant.
	@GetMapping("/api/sessions/{id}/telemetry")
	TelemetryResponse telemetry(@PathVariable long id, @RequestParam @Min(1) @Max(99) int driver,
			@RequestParam(required = false) @Min(1) Integer lap) {
		return service.telemetry(id, driver, lap);
	}

	// One lap against distance, with its tyres and estimates; no `lap`: the driver's fastest.
	@GetMapping("/api/sessions/{id}/telemetry/lap")
	LapAnalysis lap(@PathVariable long id, @RequestParam @Min(1) @Max(99) int driver,
			@RequestParam(required = false) @Min(1) Integer lap) {
		return analysis.lap(id, driver, lap);
	}

	// Two laps compared: ?driver=1&lap=39&vs=16&vsLap=36 (no laps: each driver's fastest).
	@GetMapping("/api/sessions/{id}/telemetry/compare")
	LapAnalysis.Comparison compare(@PathVariable long id, @RequestParam @Min(1) @Max(99) int driver,
			@RequestParam(required = false) @Min(1) Integer lap, @RequestParam @Min(1) @Max(99) int vs,
			@RequestParam(required = false) @Min(1) Integer vsLap) {
		return analysis.compare(id, driver, lap, vs, vsLap);
	}

}

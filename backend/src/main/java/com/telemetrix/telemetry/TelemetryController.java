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

	TelemetryController(TelemetryService service) {
		this.service = service;
	}

	// The first request for a car in a session downloads its telemetry (a few seconds); later ones are instant.
	@GetMapping("/api/sessions/{id}/telemetry")
	TelemetryResponse telemetry(@PathVariable long id, @RequestParam @Min(1) @Max(99) int driver,
			@RequestParam(required = false) @Min(1) Integer lap) {
		return service.telemetry(id, driver, lap);
	}

}

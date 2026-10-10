package com.telemetrix.result;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RaceResultsController {

	private final RaceResultsService service;

	RaceResultsController(RaceResultsService service) {
		this.service = service;
	}

	// The race's classification (from the history import; empty until it has run).
	@GetMapping("/api/races/{id}/results")
	RaceResults results(@PathVariable long id) {
		return service.results(id);
	}

	// Every car's race lap times, for the lap-pace chart.
	@GetMapping("/api/races/{id}/lap-times")
	LapTimes lapTimes(@PathVariable long id) {
		return service.lapTimes(id);
	}

}

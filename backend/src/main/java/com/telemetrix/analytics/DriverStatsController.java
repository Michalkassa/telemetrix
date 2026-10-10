package com.telemetrix.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DriverStatsController {

	private final DriverStatsService stats;

	DriverStatsController(DriverStatsService stats) {
		this.stats = stats;
	}

	// A driver's career statistics; `id` is the driver's ref ("hamilton", "max_verstappen").
	@GetMapping("/api/drivers/{id}/stats")
	DriverStats stats(@PathVariable String id) {
		return stats.stats(id);
	}

}

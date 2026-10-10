package com.telemetrix.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ConstructorStatsController {

	private final ConstructorStatsService stats;

	ConstructorStatsController(ConstructorStatsService stats) {
		this.stats = stats;
	}

	// Every constructor that has raced, the latest season first, with the seasons it raced in.
	@GetMapping("/api/constructors")
	java.util.List<ConstructorSummary> directory() {
		return stats.directory();
	}

	// A constructor's season; `id` is the constructor's ref ("mercedes", "red_bull"), the season its latest
	// when not given.
	@GetMapping("/api/constructors/{id}/stats")
	ConstructorStats stats(@PathVariable String id, @RequestParam(required = false) Integer season) {
		return stats.stats(id, season);
	}

}

package com.telemetrix.analytics;

import com.telemetrix.common.Inputs;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DriverStatsController {

	private final DriverStatsService stats;

	DriverStatsController(DriverStatsService stats) {
		this.stats = stats;
	}

	// Every driver who has raced, the latest season first.
	@GetMapping("/api/drivers")
	java.util.List<DriverSummary> directory() {
		return stats.directory();
	}

	// Every Grand Prix a driver entered, the newest first.
	@GetMapping("/api/drivers/{id}/races")
	java.util.List<RaceLogEntry> races(@PathVariable @Pattern(regexp = Inputs.REF) String id) {
		return stats.raceLog(id);
	}

	// A driver's career statistics; `id` is the driver's ref ("hamilton", "max_verstappen").
	@GetMapping("/api/drivers/{id}/stats")
	DriverStats stats(@PathVariable @Pattern(regexp = Inputs.REF) String id) {
		return stats.stats(id);
	}

}

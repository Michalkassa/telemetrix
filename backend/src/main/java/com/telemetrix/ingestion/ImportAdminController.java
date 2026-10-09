package com.telemetrix.ingestion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Manual imports. Unsecured until Step 37 (Security), when these become ADMIN-only.
@RestController
@RequestMapping("/api/admin/import")
class ImportAdminController {

	private final CalendarImporter calendarImporter;

	private final RaceImporter raceImporter;

	ImportAdminController(CalendarImporter calendarImporter, RaceImporter raceImporter) {
		this.calendarImporter = calendarImporter;
		this.raceImporter = raceImporter;
	}

	@PostMapping("/seasons/{year}")
	ImportReport importSeason(@PathVariable @Min(1950) @Max(2100) int year) {
		return calendarImporter.importSeason(year);
	}

	@PostMapping("/races/{id}")
	ImportReport importRace(@PathVariable long id) {
		return raceImporter.importRace(id);
	}

}

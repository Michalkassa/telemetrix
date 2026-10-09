package com.telemetrix.ingestion;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.ingestion.history.HistoryImportService;
import com.telemetrix.ingestion.history.HistoryImportStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

	private final HistoryImportService historyImport;

	ImportAdminController(CalendarImporter calendarImporter, RaceImporter raceImporter,
			HistoryImportService historyImport) {
		this.calendarImporter = calendarImporter;
		this.raceImporter = raceImporter;
		this.historyImport = historyImport;
	}

	@PostMapping("/seasons/{year}")
	ImportReport importSeason(@PathVariable @Min(1950) @Max(2100) int year) {
		return calendarImporter.importSeason(year);
	}

	@PostMapping("/races/{id}")
	ImportReport importRace(@PathVariable long id) {
		return raceImporter.importRace(id);
	}

	// The whole Jolpica history (1950 on): runs in the background for a few minutes.
	@PostMapping("/history")
	ResponseEntity<HistoryImportStatus> importHistory() {
		return ResponseEntity.accepted().body(historyImport.start());
	}

	@GetMapping("/history")
	HistoryImportStatus lastHistoryImport() {
		return historyImport.lastRun().orElseThrow(() -> new NotFoundException("History import", "run"));
	}

}

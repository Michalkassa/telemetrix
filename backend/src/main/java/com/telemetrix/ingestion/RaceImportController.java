package com.telemetrix.ingestion;

import com.telemetrix.ingestion.RaceImportCoordinator.ImportStatus;
import com.telemetrix.ingestion.RaceImportCoordinator.State;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// A race page calls POST .../import when it opens, then polls GET .../import-status while the state
// is IMPORTING ("downloading race data...").
@RestController
@RequestMapping("/api/races/{id}")
class RaceImportController {

	private final RaceImportCoordinator coordinator;

	RaceImportController(RaceImportCoordinator coordinator) {
		this.coordinator = coordinator;
	}

	@GetMapping("/import-status")
	ImportStatus status(@PathVariable long id) {
		return coordinator.status(id);
	}

	@PostMapping("/import")
	ResponseEntity<ImportStatus> open(@PathVariable long id) {
		ImportStatus status = coordinator.open(id);
		return status.state() == State.IMPORTING ? ResponseEntity.accepted().body(status) : ResponseEntity.ok(status);
	}

}

package com.telemetrix.eventstore;

import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// Unsecured until Step 37 (Security), when this becomes ADMIN-only.
@RestController
class ProjectionsController {

	record Rebuilt(List<String> rebuilt) {
	}

	private final Projections projections;

	ProjectionsController(Projections projections) {
		this.projections = projections;
	}

	// throws every read model (timeline, race-state snapshots, standings, analytics) away and rebuilds it
	// from the event streams
	@PostMapping("/api/admin/projections/rebuild")
	Rebuilt rebuild() {
		return new Rebuilt(projections.rebuildAll());
	}

}

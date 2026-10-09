package com.telemetrix.replay;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReplayController {

	private final ReplayService service;

	ReplayController(ReplayService service) {
		this.service = service;
	}

	// The whole race for the browser's player (open the race first: POST /api/races/{id}/import).
	@GetMapping("/api/sessions/{id}/replay")
	ReplayResponse replay(@PathVariable long id) {
		return service.replayResponse(id);
	}

	// Every car at one moment: ?at=2024-03-02T15:30:00Z
	@GetMapping("/api/sessions/{id}/replay/frame")
	PlaybackFrame frame(@PathVariable long id, @RequestParam Instant at) {
		return service.frame(id, at);
	}

}

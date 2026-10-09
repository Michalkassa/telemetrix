package com.telemetrix.race;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Seasons and races (docs/api.md).
@RestController
@RequestMapping("/api")
class RaceController {

	private final RaceQueryService service;

	RaceController(RaceQueryService service) {
		this.service = service;
	}

	@GetMapping("/seasons")
	List<RaceDtos.SeasonResponse> seasons() {
		return service.seasons();
	}

	@GetMapping("/seasons/{year}/races")
	RaceDtos.SeasonRacesResponse racesOf(@PathVariable @Min(1950) @Max(2100) int year) {
		return service.racesOf(year);
	}

	@GetMapping("/races/{id}")
	RaceDtos.RaceDetail race(@PathVariable long id) {
		return service.race(id);
	}

}

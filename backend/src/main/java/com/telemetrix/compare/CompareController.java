package com.telemetrix.compare;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CompareController {

	private final CompareService compare;

	CompareController(CompareService compare) {
		this.compare = compare;
	}

	// One circuit's Grand Prix in two to four years: /api/compare/circuits/monza?years=2023,2024
	@GetMapping("/api/compare/circuits/{ref}")
	CircuitComparison circuit(@PathVariable String ref, @RequestParam List<Integer> years) {
		return compare.circuit(ref, years);
	}

	// Every year a circuit held a Grand Prix, to compare with: /api/compare/circuits/monza/seasons
	@GetMapping("/api/compare/circuits/{ref}/seasons")
	List<Integer> circuitSeasons(@PathVariable String ref) {
		return compare.circuitSeasons(ref);
	}

	// Two drivers through a season: /api/compare/drivers?a=max_verstappen&b=leclerc&season=2024
	@GetMapping("/api/compare/drivers")
	DriverComparison drivers(@RequestParam String a, @RequestParam String b, @RequestParam int season) {
		return compare.drivers(a, b, season);
	}

}

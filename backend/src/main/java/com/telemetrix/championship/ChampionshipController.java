package com.telemetrix.championship;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChampionshipController {

	private final ChampionshipService championship;

	private final LiveStandingsService live;

	private final SeasonPagesService pages;

	ChampionshipController(ChampionshipService championship, LiveStandingsService live, SeasonPagesService pages) {
		this.championship = championship;
		this.live = live;
		this.pages = pages;
	}

	// Every Grand Prix result of the season, a row per driver in championship order.
	@GetMapping("/api/seasons/{year}/results-grid")
	SeasonPages.ResultsGrid resultsGrid(@PathVariable int year) {
		return pages.resultsGrid(year);
	}

	// Each driver's championship points after every round.
	@GetMapping("/api/seasons/{year}/progression")
	SeasonPages.Progression progression(@PathVariable int year) {
		return pages.progression(year);
	}

	// Drivers' and constructors' standings after a round (default: the latest).
	@GetMapping("/api/seasons/{year}/standings")
	ChampionshipStandings standings(@PathVariable int year, @RequestParam(required = false) Integer round) {
		return championship.standings(year, round);
	}

	// The drivers' championship if the race finished at that moment: ?at=2024-03-02T16:00:00Z
	@GetMapping("/api/sessions/{id}/replay/championship")
	LiveStandings live(@PathVariable long id, @RequestParam Instant at) {
		return this.live.at(id, at);
	}

}

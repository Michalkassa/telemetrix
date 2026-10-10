package com.telemetrix.championship;

import java.util.List;

/** GET /api/seasons/{year}/standings: the standings after `round` of `rounds` raced. */
public record ChampionshipStandings(int season, int round, int rounds, List<Driver> drivers, List<Team> constructors) {

	public record Driver(int position, String ref, String code, String givenName, String surname, String team,
			double points, int wins) {
	}

	public record Team(int position, String ref, String name, double points, int wins) {
	}

}

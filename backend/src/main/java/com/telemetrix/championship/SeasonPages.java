package com.telemetrix.championship;

import java.time.LocalDate;
import java.util.List;

/** The Seasons lens' data: a race-by-race results grid, and every driver's points round by round. */
public final class SeasonPages {

	private SeasonPages() {
	}

	/** GET /api/seasons/{year}/results-grid: drivers in championship order, a cell per Grand Prix they ran. */
	public record ResultsGrid(int season, List<Race> races, List<Row> drivers) {
	}

	public record Race(int round, long raceId, String name, LocalDate date) {
	}

	public record Row(int position, String ref, String code, String surname, double points, List<Cell> cells) {
	}

	/** `position` null: not classified (`status` says why). */
	public record Cell(int round, long raceId, Integer position, double points, String status) {
	}

	/** GET /api/seasons/{year}/progression: each driver's championship points after every round. */
	public record Progression(int season, List<Line> drivers) {
	}

	public record Line(String ref, String code, String surname, List<RoundPoints> rounds) {
	}

	public record RoundPoints(int round, double points) {
	}

}

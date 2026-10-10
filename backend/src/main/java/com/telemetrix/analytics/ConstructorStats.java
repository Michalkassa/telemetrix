package com.telemetrix.analytics;

import java.util.List;

public record ConstructorStats(Constructor constructor, int season, Reliability reliability, PitStops pitStops,
		Pace pace, Trend trend, List<Strategy> strategies) {

	public record Constructor(String ref, String name) {
	}

	/** DNFs by cause; `unknown` is a retirement the data gives no cause for ("Retired", 2023 on). */
	public record Reliability(int starts, int finishes, int dnfs, int mechanical, int incident, int unknown,
			List<Cause> causes) {
	}

	public record Cause(String cause, int count) {
	}

	/** The median time in the pit lane, and where that ranks among the season's teams (1 = quickest). */
	public record PitStops(int stops, Double medianLaneSeconds, Integer rank, int teams,
			Double fieldMedianLaneSeconds) {
	}

	/** The median, over the season's races, of the gap to the fastest team (%). */
	public record Pace(Double raceGapPercent, Double qualifyingGapPercent) {
	}

	/** The gaps round by round, and their slope (percentage points a round; negative = closing in). */
	public record Trend(List<Round> rounds, Double qualifyingSlope, Double raceSlope) {
	}

	public record Round(int round, String race, Double qualifyingGapPercent, Double raceGapPercent) {
	}

	/** Positions gained (grid to finish) by the number of pit stops, over the team's classified finishes. */
	public record Strategy(int stops, int finishes, double averagePositionsGained) {
	}

}

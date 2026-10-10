package com.telemetrix.analytics;

import java.util.List;

public record DriverStats(Driver driver, Career career, WetDry wetDry, List<Track> tracks, PitStops pitStops,
		List<Tyre> tyres, List<Season> seasons) {

	public record Driver(String ref, String code, String givenName, String surname) {
	}

	public record Career(int starts, int wins, int podiums, int dnfs, int poles, int fastestLaps, Double averageFinish,
			int positionsGained, Double averagePositionsGained, double points) {
	}

	public record Conditions(int races, int wins, int podiums, Double averageFinish) {
	}

	public record WetDry(Conditions wet, Conditions dry) {
	}

	public record Track(String circuit, String name, int starts, int wins, int podiums, Integer bestFinish,
			Double averageFinish) {
	}

	public record PitStops(int stops, Double stopsPerRace, Double averageLaneSeconds) {
	}

	public record Tyre(String compound, int stints, int laps, double share, double averageStintLaps) {
	}

	public record Season(int season, int starts, int wins, double points, int careerWins) {
	}

}

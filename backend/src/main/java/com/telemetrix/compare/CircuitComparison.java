package com.telemetrix.compare;

import java.time.LocalDate;
import java.util.List;

/** One circuit's Grand Prix in several years, side by side. */
public record CircuitComparison(Circuit circuit, List<Year> years) {

	/** `seasons`: every year the circuit held a Grand Prix, to pick others from. */
	public record Circuit(String ref, String name, List<Integer> seasons) {
	}

	/**
	 * A year's race. `strategySource`: TYRES (OpenF1 stints, once the race was opened in the app), PIT_STOPS
	 * (stints between the history's pit stops, no compounds) or NONE.
	 */
	public record Year(int season, long raceId, String race, LocalDate date, Driver winner, FastestLap fastestLap,
			List<LapPace> pace, String strategySource, List<Strategy> strategies) {
	}

	public record Driver(String ref, String code, String surname, String team) {
	}

	public record FastestLap(long ms, int lap, String code) {
	}

	/** The field's median lap time on a lap, and the winner's. */
	public record LapPace(int lap, Long medianMs, Long winnerMs) {
	}

	/** A car's race, in finishing order: its stops and its stints (compound null without OpenF1 data). */
	public record Strategy(Integer position, String ref, String code, String surname, String team, int stops,
			List<Stint> stints) {
	}

	public record Stint(String compound, int fromLap, int toLap) {
	}

}

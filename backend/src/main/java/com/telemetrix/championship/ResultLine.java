package com.telemetrix.championship;

/** One car's result in one Grand Prix or sprint: its finishing position (null: not classified) and the points awarded. */
public record ResultLine(int round, Kind kind, String driver, String constructor, Integer position, double points) {

	public enum Kind {
		GRAND_PRIX, SPRINT
	}

}

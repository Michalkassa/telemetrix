package com.telemetrix.cache;

// The Redis caches (docs/adr/0008) by name.
public final class Caches {

	public static final String TIMELINES = "timelines";

	public static final String REPLAYS = "replays";

	public static final String SNAPSHOTS = "snapshots";

	public static final String STANDINGS = "standings";

	public static final String TELEMETRY_LAPS = "telemetry-laps";

	private Caches() {
	}

}

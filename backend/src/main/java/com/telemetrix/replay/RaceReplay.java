package com.telemetrix.replay;

import com.telemetrix.circuit.CircuitLayout;
import com.telemetrix.circuit.TrackOutline;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Everything needed to replay a session: each car's progress timeline, pit stops and driver, the
// track map, and when the chequered flag fell (the winner's last timing point).
public record RaceReplay(long sessionId, long raceId, String sessionName, Instant clockStart, Instant chequeredAt,
		int totalLaps, CircuitLayout layout, List<Driver> drivers, Map<Integer, ProgressTimeline> timelines,
		Map<Integer, List<PitWindow>> pits) {

	public record Driver(int number, String code, String name, String team, String teamColour) {
	}

	public record PitWindow(Integer lap, Instant from, Instant to) {
	}

	public RaceReplay {
		timelines = new TreeMap<>(timelines);
	}

	public ProgressTimeline timeline(int carNumber) {
		return timelines.get(carNumber);
	}

	public List<Integer> carNumbers() {
		return List.copyOf(timelines.keySet());
	}

	/** The track map's outline, or null when there's no map. */
	public TrackOutline outline() {
		return layout == null ? null : layout.outline();
	}

}

package com.telemetrix.replay;

import com.telemetrix.circuit.LayoutResponse;
import java.time.Instant;
import java.util.List;

// GET /api/sessions/{id}/replay: the whole race at once, for the browser to play like a video
// (docs/adr/0004). Times are milliseconds after `clockStart`; `laps` are laps done (9.25 = a quarter of
// the way round lap 10), interpolated linearly between consecutive points. Pit windows are in the same
// milliseconds.
public record ReplayResponse(long sessionId, long raceId, String session, Instant clockStart, Instant chequeredAt,
		int totalLaps, LayoutResponse layout, List<Driver> drivers, List<CarTiming> timing) {

	public record Driver(int number, String code, String name, String team, String teamColour) {
	}

	public record CarTiming(int number, long[] t, double[] laps, List<PitWindow> pits, boolean finished,
			Long retiredAt) {
	}

	public record PitWindow(Integer lap, long from, long to) {
	}

}

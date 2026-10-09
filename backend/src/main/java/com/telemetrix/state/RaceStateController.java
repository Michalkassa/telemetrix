package com.telemetrix.state;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RaceStateController {

	record SnapshotResponse(long t, Instant at, int lap, SnapshotReason reason, Flag flag,
			Map<Integer, DerivedState.Car> cars) {
	}

	private final RaceStateService states;

	RaceStateController(RaceStateService states) {
		this.states = states;
	}

	// The whole race state at a moment: ?at=2023-09-17T12:40:00Z
	@GetMapping("/api/sessions/{id}/replay/state")
	RaceState state(@PathVariable long id, @RequestParam Instant at) {
		return states.stateAt(id, at);
	}

	// The derived state (flag, tyres, pit stops, penalties) once per lap and at each key event, for the
	// browser to start from when scrubbing. `t` is milliseconds after the replay's clockStart.
	@GetMapping("/api/sessions/{id}/replay/snapshots")
	List<SnapshotResponse> snapshots(@PathVariable long id) {
		long zero = states.clockStart(id).toEpochMilli();
		return states.shippedSnapshots(id).stream()
				.map(s -> new SnapshotResponse(s.at().toEpochMilli() - zero, s.at(), s.lap(), s.reason(),
						s.state().flag(), new TreeMap<>(s.state().cars())))
				.toList();
	}

}

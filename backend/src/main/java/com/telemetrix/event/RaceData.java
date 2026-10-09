package com.telemetrix.event;

import com.telemetrix.racecontrol.PenaltyKind;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import com.telemetrix.replay.ProgressTimeline;
import com.telemetrix.replay.RaceReplay.PitWindow;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Everything the event detectors look at for one race session, loaded once (RaceDataLoader): each car's
// progress timeline and pit windows (the replay's), laps, tyre stints, track status, race-control
// messages, penalties, team radio and weather. Plain data, so detectors are tested on tiny hand-built races.
public record RaceData(Instant clockStart, Instant chequeredAt, int totalLaps, Map<Integer, String> codes,
		Map<Integer, ProgressTimeline> timelines, Map<Integer, List<PitWindow>> pits, List<LapTime> laps,
		List<Stint> stints, List<TrackStatusPeriod> trackStatus, List<Message> raceControl,
		List<PenaltyIssued> penalties, List<RadioClip> radio, List<Weather> weather) {

	public record LapTime(int car, int lap, Instant startsAt, Integer lapMs) {
	}

	/** `ageAtStart`: laps already on the tyres when they were fitted. */
	public record Stint(int car, int stint, String compound, int lapStart, int ageAtStart) {
	}

	public record Message(Instant at, Integer lap, String text) {
	}

	public record PenaltyIssued(int car, PenaltyKind kind, Integer amount, String reason, Instant at, Integer lap) {
	}

	public record RadioClip(int car, Instant at, String url) {
	}

	public record Weather(Instant at, boolean rainfall) {
	}

	/** A car's code ("VER"), or its number when the driver isn't known. */
	public String code(int car) {
		return codes.getOrDefault(car, String.valueOf(car));
	}

	/** The leader's lap at a moment (1 before the start, never past the last lap). */
	public int lapAt(Instant at) {
		double lapsDone = timelines.values().stream().mapToDouble(t -> t.lapsDoneAt(at)).max().orElse(0);
		return lapOf(lapsDone);
	}

	/** The lap a car with this many laps done is on. */
	public int lapOf(double lapsDone) {
		int lap = (int) Math.floor(lapsDone) + 1;
		return Math.max(1, totalLaps > 0 ? Math.min(lap, totalLaps) : lap);
	}

	/** Car numbers in race order at a moment: furthest round first (level cars by number). */
	public List<Integer> runningOrder(Instant at) {
		return timelines.entrySet().stream()
				.sorted(Comparator.<Map.Entry<Integer, ProgressTimeline>>comparingDouble(e -> -e.getValue().lapsDoneAt(at))
						.thenComparing(Map.Entry::getKey))
				.map(Map.Entry::getKey)
				.toList();
	}

	/** A car's race position (1 = leading) at a moment; 0 if it isn't in the race. */
	public int positionAt(int car, Instant at) {
		return runningOrder(at).indexOf(car) + 1;
	}

	/** When a car stopped for good before the chequered flag; null if it finished (or isn't known). */
	public Instant retiredAt(int car) {
		ProgressTimeline timeline = timelines.get(car);
		if (timeline == null || timeline.isEmpty() || !timeline.lastAt().isBefore(chequeredAt)) {
			return null;
		}
		return timeline.lastAt();
	}

	/** Whether a car was in the pit lane at any time within `margin` of a moment. */
	public boolean pittingAround(int car, Instant at, Duration margin) {
		return pits.getOrDefault(car, List.of()).stream()
				.anyMatch(p -> !at.isBefore(p.from().minus(margin)) && !at.isAfter(p.to().plus(margin)));
	}

}

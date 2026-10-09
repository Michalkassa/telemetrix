package com.telemetrix.event;

import com.telemetrix.racecontrol.TrackStatus;
import com.telemetrix.replay.ProgressTimeline;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

// Overtakes from the progress-based running order (docs/adr/0004), sampled every second: car A goes from
// behind car B to ahead of it. It counts only if
// - B was ahead 5 s before and A is still ahead 5 s after (not a swap undone at once — the timing is
//   interpolated within each sector, so close cars can flicker);
// - neither car was in the pit lane around then, nor on its pit lap or out-lap (a pit-cycle swap isn't an
//   overtake; the pit lane is timed within a whole sector, so a pitting car looks slow well before it);
// - the track wasn't under the safety car, VSC or red flag (no passing allowed: swaps then are pit stops);
// - B hadn't stopped (passing a retired car isn't one either);
// - the leader has completed lap 1 (the start's order is unknown until the cars reach the timing points).
// Within a sector a pass shows up when the timing does, so `at` can be a few seconds late.
@Component
class OvertakeDetector implements EventDetector {

	private static final Duration STEP = Duration.ofSeconds(1);

	private static final Duration HOLD = Duration.ofSeconds(5);

	private static final Duration PIT_MARGIN = Duration.ofSeconds(30);

	private static final Set<TrackStatus> NEUTRALISED = Set.of(TrackStatus.SAFETY_CAR, TrackStatus.VSC,
			TrackStatus.VSC_ENDING, TrackStatus.RED);

	@Override
	public List<RaceEvent> detect(RaceData race) {
		List<RaceEvent> events = new ArrayList<>();
		Instant from = firstLapDone(race);
		if (from == null) {
			return events;
		}
		List<Integer> previous = race.runningOrder(from);
		for (Instant t = from.plus(STEP); !t.isAfter(race.chequeredAt()); t = t.plus(STEP)) {
			List<Integer> order = race.runningOrder(t);
			for (int position = 0; position < order.size(); position++) {
				int a = order.get(position);
				int before = previous.indexOf(a);
				// every car A was behind and is now behind A
				for (int b : previous.subList(0, Math.max(before, 0))) {
					if (order.indexOf(b) > position && counts(race, a, b, t)) {
						events.add(new RaceEvent(EventType.OVERTAKE, t, race.lapAt(t), null, null, Severity.MEDIUM,
								List.of(a, b), race.code(a) + " overtakes " + race.code(b) + " for P" + (position + 1),
								null));
					}
				}
			}
			previous = order;
		}
		return events;
	}

	private static boolean counts(RaceData race, int a, int b, Instant t) {
		Map<Integer, ProgressTimeline> cars = race.timelines();
		Instant before = t.minus(HOLD);
		Instant after = t.plus(HOLD).isAfter(race.chequeredAt()) ? race.chequeredAt() : t.plus(HOLD);
		boolean bWasAhead = cars.get(b).lapsDoneAt(before) > cars.get(a).lapsDoneAt(before);
		boolean aStaysAhead = cars.get(a).lapsDoneAt(after) > cars.get(b).lapsDoneAt(after);
		Instant bStopped = race.retiredAt(b);
		return bWasAhead && aStaysAhead && (bStopped == null || bStopped.isAfter(after)) && !neutralised(race, t)
				&& !pitting(race, a, t) && !pitting(race, b, t);
	}

	private static boolean pitting(RaceData race, int car, Instant t) {
		int lap = race.lapOf(race.timelines().get(car).lapsDoneAt(t));
		return race.pittingAround(car, t, PIT_MARGIN) || race.pits().getOrDefault(car, List.of()).stream()
				.anyMatch(p -> p.lap() != null && (p.lap() == lap || p.lap() == lap - 1));
	}

	private static boolean neutralised(RaceData race, Instant t) {
		return race.trackStatus().stream()
				.filter(p -> NEUTRALISED.contains(p.status()))
				.anyMatch(p -> !t.isBefore(p.from()) && (p.to() == null || !t.isAfter(p.to())));
	}

	// when the leader first has a whole lap done
	private static Instant firstLapDone(RaceData race) {
		return race.timelines().values().stream()
				.map(timeline -> timeline.reachedAt(1.0))
				.filter(java.util.Objects::nonNull)
				.min(Instant::compareTo)
				.orElse(null);
	}

}

package com.telemetrix.championship;

import com.telemetrix.championship.LiveStandingsService.Context;
import com.telemetrix.championship.LiveStandingsService.Placed;
import com.telemetrix.event.EventDetector;
import com.telemetrix.event.EventType;
import com.telemetrix.event.RaceData;
import com.telemetrix.event.RaceEvent;
import com.telemetrix.event.Severity;
import com.telemetrix.replay.ProgressTimeline;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

// The championship lead changing hands during a race, "if the race finished now": the drivers' table
// (the points before the race plus what the running order earns) checked whenever the leader starts a
// lap, a car retires, and at the flag. Not in the first round (no lead before it) or without the history.
@Component
class ChampionshipLeadDetector implements EventDetector {

	private final LiveStandingsService live;

	ChampionshipLeadDetector(LiveStandingsService live) {
		this.live = live;
	}

	@Override
	public List<RaceEvent> detect(RaceData race) {
		Context context = live.context(race.sessionId()).orElse(null);
		if (context == null || context.before().isEmpty()) {
			return List.of();
		}
		String leader = context.before().entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();

		List<RaceEvent> events = new ArrayList<>();
		for (Instant t : moments(race)) {
			var table = LiveStandingsService.table(context, order(race, t), LiveStandingsService.fastestLapSoFar(race, t));
			if (table.isEmpty() || table.getFirst().ref().equals(leader)) {
				continue;
			}
			var now = table.getFirst();
			String previous = leader;
			Integer previousCar = context.cars().entrySet().stream().filter(e -> e.getValue().ref().equals(previous))
					.map(Map.Entry::getKey).findFirst().orElse(null);
			events.add(new RaceEvent(EventType.CHAMPIONSHIP_LEAD, t, race.lapAt(t), null, null, Severity.HIGH,
					previousCar == null ? List.of(now.number()) : List.of(now.number(), previousCar),
					"Championship lead: " + name(now.code(), now.surname()) + " takes it from "
							+ name(previousCar == null ? null : context.cars().get(previousCar).code(), previous)
							+ " (if the race finished now)",
					null));
			leader = now.ref();
		}
		return events;
	}

	// whenever the leader starts a lap, a car stops, and the flag
	private static TreeSet<Instant> moments(RaceData race) {
		TreeSet<Instant> moments = new TreeSet<>();
		for (int k = 1; k <= race.totalLaps(); k++) {
			int laps = k;
			race.timelines().values().stream().map(t -> t.reachedAt(laps)).filter(Objects::nonNull)
					.min(Instant::compareTo).ifPresent(moments::add);
		}
		race.timelines().keySet().stream().map(race::retiredAt).filter(Objects::nonNull).forEach(moments::add);
		moments.add(race.chequeredAt());
		return moments;
	}

	// the running order at t; a car that has stopped is not classified
	private static List<Placed> order(RaceData race, Instant t) {
		List<Placed> order = new ArrayList<>();
		int position = 1;
		for (int car : race.runningOrder(t)) {
			Instant stopped = race.retiredAt(car);
			ProgressTimeline timeline = race.timelines().get(car);
			boolean out = (stopped != null && !t.isBefore(stopped)) || timeline.isEmpty() || t.isBefore(timeline.firstAt());
			order.add(new Placed(car, out ? null : position++));
		}
		return order;
	}

	private static String name(String code, String fallback) {
		return code != null ? code : fallback;
	}

}

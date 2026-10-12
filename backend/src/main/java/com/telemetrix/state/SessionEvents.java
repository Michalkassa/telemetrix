package com.telemetrix.state;

import com.telemetrix.event.RaceData;
import com.telemetrix.event.RaceData.LapTime;
import com.telemetrix.event.RaceData.Stint;
import com.telemetrix.event.RaceEvent;
import com.telemetrix.racecontrol.RaceControlParser;
import com.telemetrix.racecontrol.RaceControlSignal;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import com.telemetrix.replay.RaceReplay.PitWindow;
import com.telemetrix.state.SessionEvent.Detected;
import com.telemetrix.state.SessionEvent.DrsChanged;
import com.telemetrix.state.SessionEvent.FlagChanged;
import com.telemetrix.state.SessionEvent.PenaltyGiven;
import com.telemetrix.state.SessionEvent.PitIn;
import com.telemetrix.state.SessionEvent.PitOut;
import com.telemetrix.state.SessionEvent.SectorFlagged;
import com.telemetrix.state.SessionEvent.SessionRecorded;
import com.telemetrix.state.SessionEvent.TrackCleared;
import com.telemetrix.state.SessionEvent.TyresFitted;
import com.telemetrix.state.SessionEvent.WeatherReported;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

// The write side's rules: a race session's stream worked out from its imported data — the track status,
// pit stops, tyre stints, penalties, race-control messages and weather — plus the events the timeline's
// detectors found. Pure, so the same data always gives the same stream.
public final class SessionEvents {

	private SessionEvents() {
	}

	/** The stream: SessionRecorded, the starting tyres, then every event in time order. */
	public static List<SessionEvent> derive(RaceData race, List<RaceEvent> detected) {
		record Ordered(SessionEvent event, int order) {
		}
		List<SessionEvent> stream = new ArrayList<>();
		stream.add(new SessionRecorded(race.clockStart(), race.chequeredAt(), race.totalLaps(), race.codes()));
		for (Stint stint : race.stints()) {
			if (stint.stint() == firstStint(race, stint.car())) {
				stream.add(new TyresFitted(null, stint.car(), stint.compound(), stint.lapStart(), stint.ageAtStart()));
			}
		}

		// at the same moment: a period ending, then the next starting; pit exit before entry; then the rest
		List<Ordered> timed = new ArrayList<>();
		for (TrackStatusPeriod p : race.trackStatus()) {
			if (p.to() != null) {
				timed.add(new Ordered(new FlagChanged(p.to(), Flag.GREEN), 0));
			}
		}
		for (TrackStatusPeriod p : race.trackStatus()) {
			timed.add(new Ordered(new FlagChanged(p.from(), Flag.of(p.status())), 1));
		}
		race.pits().forEach((car, stops) -> stops.forEach(stop -> {
			timed.add(new Ordered(new PitOut(stop.to(), car), 2));
			timed.add(new Ordered(new PitIn(stop.from(), car), 3));
		}));
		for (Stint stint : race.stints()) {
			if (stint.stint() == firstStint(race, stint.car())) {
				continue;
			}
			Instant at = fittedAt(race, stint);
			if (at != null) {
				timed.add(new Ordered(new TyresFitted(at, stint.car(), stint.compound(), stint.lapStart(),
						stint.ageAtStart()), 4));
			}
		}
		race.penalties().forEach(p -> timed.add(new Ordered(new PenaltyGiven(p.at(), p.car(), p.kind(), p.amount()), 5)));
		for (RaceData.Message m : race.raceControl()) {
			String text = m.text() == null ? "" : m.text().trim();
			switch (RaceControlParser.parse(text)) {
				case RaceControlSignal.SectorFlag f -> timed.add(new Ordered(new SectorFlagged(m.at(), f.sector(),
						!f.yellow() ? null : text.startsWith("DOUBLE") ? SectorFlag.DOUBLE_YELLOW : SectorFlag.YELLOW), 6));
				case RaceControlSignal.TrackClear c -> timed.add(new Ordered(new TrackCleared(m.at()), 6));
				default -> {
					if (text.equals("DRS ENABLED") || text.equals("DRS DISABLED")) { // not one zone: DRS as a whole
						timed.add(new Ordered(new DrsChanged(m.at(), text.equals("DRS ENABLED")), 6));
					}
				}
			}
		}
		race.weather().forEach(w -> timed.add(new Ordered(new WeatherReported(w.at(), w.airTemperature(),
				w.trackTemperature(), w.humidity(), w.rainfall(), w.windSpeed()), 7)));
		detected.stream().sorted(Comparator.comparing(RaceEvent::at).thenComparing(RaceEvent::type))
				.forEach(e -> timed.add(new Ordered(new Detected(e), 8)));

		// a stable sort: same moment and kind keep the order they came in
		timed.sort(Comparator.comparing((Ordered o) -> o.event().at()).thenComparingInt(Ordered::order));
		timed.forEach(o -> stream.add(o.event()));
		return List.copyOf(stream);
	}

	private static int firstStint(RaceData race, int car) {
		return race.stints().stream().filter(s -> s.car() == car).mapToInt(Stint::stint).min().orElse(1);
	}

	// at the pit stop on the lap before the stint (as the car leaves), else when the stint's first lap began
	private static Instant fittedAt(RaceData race, Stint stint) {
		return race.pits().getOrDefault(stint.car(), List.of()).stream()
				.filter(p -> Objects.equals(p.lap(), stint.lapStart() - 1))
				.map(PitWindow::to)
				.findFirst()
				.orElseGet(() -> race.laps().stream()
						.filter(l -> l.car() == stint.car() && l.lap() == stint.lapStart() && l.startsAt() != null)
						.map(LapTime::startsAt)
						.findFirst()
						.orElse(null));
	}

}

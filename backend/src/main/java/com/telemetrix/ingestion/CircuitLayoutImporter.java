package com.telemetrix.ingestion;

import com.telemetrix.circuit.Circuit;
import com.telemetrix.circuit.CircuitLayout;
import com.telemetrix.circuit.CircuitLayoutStore;
import com.telemetrix.circuit.TrackOutline;
import com.telemetrix.circuit.TrackOutline.Point;
import com.telemetrix.ingestion.multiviewer.MultiviewerCircuit;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.ingestion.openf1.OpenF1Location;
import com.telemetrix.race.Race;
import com.telemetrix.race.Session;
import com.telemetrix.timing.Lap;
import com.telemetrix.timing.LapRepository;
import com.telemetrix.timing.SessionPitStop;
import com.telemetrix.timing.SessionPitStopRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Gets a race's circuit map once per circuit and year, as part of the race import:
// 1. Multiviewer's map (1 request), with sector ends from its mini-sectors when the race's laps say
//    how many each sector has — otherwise estimated from sector times;
// 2. failing that, the outline of the session's fastest lap from OpenF1 positions (1 request);
// and the pit lane, traced from one car's positions through the session's first ordinary pit stop
// (1 request; none without a pit stop).
@Component
class CircuitLayoutImporter {

	private final MultiviewerClient multiviewer;

	private final OpenF1Client openF1;

	private final LapRepository laps;

	private final CircuitLayoutStore layouts;

	private final SessionPitStopRepository pitStops;

	CircuitLayoutImporter(MultiviewerClient multiviewer, OpenF1Client openF1, LapRepository laps,
			CircuitLayoutStore layouts, SessionPitStopRepository pitStops) {
		this.multiviewer = multiviewer;
		this.openF1 = openF1;
		this.laps = laps;
		this.layouts = layouts;
		this.pitStops = pitStops;
	}

	/** Drops the race's circuit map for its year, so ensureLayout fetches it again. */
	void forget(Race race) {
		layouts.delete(race.getCircuit().getId(), race.getSeason().getYear());
	}

	void ensureLayout(Race race, Session session, SectorSegments segments, ImportReport.Builder report) {
		Circuit circuit = race.getCircuit();
		int year = race.getSeason().getYear();
		if (layouts.exists(circuit.getId(), year)) {
			report.changed("layouts", false);
			return;
		}
		List<Lap> sessionLaps = laps.findBySessionOrderByDriverNumberAscLapNumberAsc(session);
		Optional<MultiviewerCircuit> map = circuit.getOpenf1CircuitKey() == null ? Optional.empty()
				: multiviewer.circuit(circuit.getOpenf1CircuitKey(), year);
		CircuitLayout layout = map.filter(m -> m.x() != null && m.y() != null && m.x().size() >= 3)
				.map(m -> fromMultiviewer(circuit, year, m, segments, sessionLaps))
				.orElseGet(() -> fromFastestLap(circuit, year, session, sessionLaps));
		if (layout == null) {
			report.note("Race " + race.getId() + ": no track map for " + circuit.getRef() + " " + year);
			return;
		}
		layout = new CircuitLayout(layout.circuitRef(), layout.year(), layout.source(), layout.rotation(),
				layout.points(), layout.sectorEnds(), layout.sectorEndsSource(), layout.corners(),
				layout.marshalSectors(), pitLane(session));
		layouts.save(circuit.getId(), year, layout);
		report.inserted("layouts");
	}

	private static CircuitLayout fromMultiviewer(Circuit circuit, int year, MultiviewerCircuit map,
			SectorSegments segments, List<Lap> sessionLaps) {
		List<Point> points = new ArrayList<>();
		for (int i = 0; i < Math.min(map.x().size(), map.y().size()); i++) {
			points.add(new Point(map.x().get(i), map.y().get(i)));
		}
		double rotation = map.rotation() == null ? 0 : map.rotation();
		var outline = new TrackOutline(points, rotation);
		double[] ends = segments == null ? null
				: outline.sectorEndsFromMiniSectors(map.miniSectorsIndexes(), segments.sector1(), segments.sector2());
		CircuitLayout.SectorEndsSource endsFrom = CircuitLayout.SectorEndsSource.TRACK;
		if (ends == null) {
			ends = estimateSectorEnds(sessionLaps);
			endsFrom = ends == null ? null : CircuitLayout.SectorEndsSource.ESTIMATED;
		}
		List<CircuitLayout.Corner> corners = map.corners() == null ? List.of() : map.corners().stream()
				.filter(c -> c.number() != null && c.trackPosition() != null)
				.map(c -> {
					var at = new Point(c.trackPosition().x(), c.trackPosition().y());
					return new CircuitLayout.Corner(c.number(), at.x(), at.y(),
							outline.progressAtIndex(outline.nearestIndex(at)));
				})
				.toList();
		List<CircuitLayout.MarshalSector> marshalSectors = map.marshalSectors() == null ? List.of()
				: map.marshalSectors().stream()
						.filter(m -> m.number() != null && m.trackPosition() != null)
						.map(m -> new CircuitLayout.MarshalSector(m.number(), outline.progressAtIndex(
								outline.nearestIndex(new Point(m.trackPosition().x(), m.trackPosition().y())))))
						.sorted(Comparator.comparingInt(CircuitLayout.MarshalSector::number))
						.toList();
		return new CircuitLayout(circuit.getRef(), year, CircuitLayout.Source.MULTIVIEWER, rotation, points, ends,
				endsFrom, corners, marshalSectors, List.of());
	}

	// The outline traced by the fastest complete lap's positions (not drawn upright: rotation 0).
	private CircuitLayout fromFastestLap(Circuit circuit, int year, Session session, List<Lap> sessionLaps) {
		Optional<Lap> fastest = sessionLaps.stream()
				.filter(Lap::isComplete)
				.filter(l -> l.getLapMs() != null)
				.min(Comparator.comparing(Lap::getLapMs));
		if (fastest.isEmpty() || session.getOpenf1SessionKey() == null) {
			return null;
		}
		Lap lap = fastest.get();
		Instant from = lap.getStartsAt();
		Instant to = from.plusMillis(lap.getLapMs());
		List<Point> points = openF1.locations(session.getOpenf1SessionKey(), lap.getDriverNumber()).stream()
				.filter(l -> l.date() != null && !l.date().toInstant().isBefore(from) && l.date().toInstant().isBefore(to))
				.filter(l -> !isNowhere(l))
				.sorted(Comparator.comparing(OpenF1Location::date))
				.map(l -> new Point(l.x(), l.y()))
				.toList();
		if (points.size() < 3) {
			return null;
		}
		double[] ends = estimateSectorEnds(sessionLaps);
		return new CircuitLayout(circuit.getRef(), year, CircuitLayout.Source.OPENF1_LAP, 0, points, ends,
				ends == null ? null : CircuitLayout.SectorEndsSource.ESTIMATED, List.of(), List.of(), List.of());
	}

	// One car's path through the pit lane: its positions from pit entry to exit, at the session's first
	// ordinary stop (lap 5 on, 18-40 s in the pit lane: not a lap-1 repair or a long stop with the car
	// pushed back).
	private List<Point> pitLane(Session session) {
		if (session.getOpenf1SessionKey() == null) {
			return List.of();
		}
		Optional<SessionPitStop> stop = pitStops.findBySessionOrderByDriverNumberAsc(session).stream()
				.filter(p -> p.getEnteredAt() != null && p.getLaneDurationMs() != null)
				.filter(p -> p.getLapNumber() != null && p.getLapNumber() >= 5)
				.filter(p -> p.getLaneDurationMs() >= 18_000 && p.getLaneDurationMs() <= 40_000)
				.min(Comparator.comparing(SessionPitStop::getEnteredAt));
		if (stop.isEmpty()) {
			return List.of();
		}
		Instant from = stop.get().getEnteredAt();
		Instant to = from.plusMillis(stop.get().getLaneDurationMs());
		return openF1.locations(session.getOpenf1SessionKey(), stop.get().getDriverNumber(), from, to).stream()
				.filter(l -> l.date() != null && !isNowhere(l))
				.sorted(Comparator.comparing(OpenF1Location::date))
				.map(l -> new Point(l.x(), l.y()))
				.toList();
	}

	private static double[] estimateSectorEnds(List<Lap> sessionLaps) {
		return TrackOutline.estimateSectorEnds(sessionLaps.stream()
				.filter(Lap::isComplete)
				.filter(l -> l.getLapMs() != null && l.getLapMs() > 0)
				.map(l -> new int[] { l.getSector1Ms(), l.getSector2Ms(), l.getLapMs() })
				.toList());
	}

	private static boolean isNowhere(OpenF1Location l) {
		return l.x() == null || l.y() == null || (l.x() == 0 && l.y() == 0 && (l.z() == null || l.z() == 0));
	}

}

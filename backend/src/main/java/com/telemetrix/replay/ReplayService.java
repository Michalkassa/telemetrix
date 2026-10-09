package com.telemetrix.replay;

import com.telemetrix.circuit.CircuitLayout;
import com.telemetrix.circuit.CircuitLayoutStore;
import com.telemetrix.circuit.LayoutResponse;
import com.telemetrix.circuit.TrackOutline;
import com.telemetrix.common.NotFoundException;
import com.telemetrix.race.Race;
import com.telemetrix.race.Session;
import com.telemetrix.race.SessionRepository;
import com.telemetrix.replay.PlaybackFrame.Status;
import com.telemetrix.timing.Lap;
import com.telemetrix.timing.LapRepository;
import com.telemetrix.timing.SessionDriverRepository;
import com.telemetrix.timing.SessionPitStopRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Builds a session's replay from its stored timing (docs/adr/0004): one ProgressTimeline per car,
// served whole to the browser, and frames (every car at one moment) on the server.
@Service
@Transactional(readOnly = true)
public class ReplayService {

	// when the track map has no sector ends and no lap has all three sectors
	private static final double[] EVEN_SECTORS = { 1.0 / 3, 2.0 / 3 };

	// a pit stop without a recorded pit-lane time
	private static final Duration TYPICAL_PIT_LANE = Duration.ofSeconds(25);

	private final SessionRepository sessions;

	private final LapRepository laps;

	private final SessionPitStopRepository pitStops;

	private final SessionDriverRepository drivers;

	private final CircuitLayoutStore layouts;

	ReplayService(SessionRepository sessions, LapRepository laps, SessionPitStopRepository pitStops,
			SessionDriverRepository drivers, CircuitLayoutStore layouts) {
		this.sessions = sessions;
		this.laps = laps;
		this.pitStops = pitStops;
		this.drivers = drivers;
		this.layouts = layouts;
	}

	public RaceReplay replay(long sessionId) {
		Session session = sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("Session", sessionId));
		List<Lap> sessionLaps = laps.findBySessionOrderByDriverNumberAscLapNumberAsc(session);
		if (sessionLaps.isEmpty()) {
			throw new NotFoundException("Timing for session", sessionId);
		}
		Race race = session.getRace();
		CircuitLayout layout = layouts.find(race.getCircuit().getId(), race.getSeason().getYear()).orElse(null);
		double[] sectorEnds = sectorEnds(layout, sessionLaps);

		Map<Integer, ProgressTimeline> timelines = new LinkedHashMap<>();
		sessionLaps.stream()
				.collect(Collectors.groupingBy(Lap::getDriverNumber, LinkedHashMap::new, Collectors.toList()))
				.forEach((car, carLaps) -> {
					var timeline = ProgressTimeline.fromLaps(carLaps, sectorEnds[0], sectorEnds[1]);
					if (!timeline.isEmpty()) {
						timelines.put(car, timeline);
					}
				});
		if (timelines.isEmpty()) {
			throw new NotFoundException("Timing for session", sessionId);
		}

		// the winner went furthest, first; the flag fell when they crossed the line for the last time
		ProgressTimeline winner = timelines.values().stream()
				.max(Comparator.comparingDouble(ProgressTimeline::finalLapsDone)
						.thenComparing(ProgressTimeline::lastAt, Comparator.reverseOrder()))
				.orElseThrow();
		Instant clockStart = timelines.values().stream().map(ProgressTimeline::firstAt).min(Comparator.naturalOrder())
				.orElseThrow();

		Map<Integer, List<RaceReplay.PitWindow>> pits = pitStops.findBySessionOrderByDriverNumberAsc(session).stream()
				.filter(p -> p.getEnteredAt() != null)
				.collect(Collectors.groupingBy(p -> p.getDriverNumber(), Collectors.mapping(p -> new RaceReplay.PitWindow(
						p.getLapNumber() == null ? null : p.getLapNumber().intValue(), p.getEnteredAt(),
						p.getLaneDurationMs() != null ? p.getEnteredAt().plusMillis(p.getLaneDurationMs())
								: p.getEnteredAt().plus(TYPICAL_PIT_LANE)),
						Collectors.toList())));
		var sessionDrivers = drivers.findBySessionOrderByDriverNumberAsc(session).stream()
				.map(d -> new RaceReplay.Driver(d.getDriverNumber(), d.getCode(), d.getFullName(), d.getTeamName(),
						d.getTeamColour()))
				.toList();

		return new RaceReplay(sessionId, race.getId(), session.getName(), clockStart, winner.lastAt(),
				(int) Math.floor(winner.finalLapsDone()), layout, sessionDrivers, timelines, pits);
	}

	/** Every car at one moment, ordered, with its place on the (rotated) track map. */
	public PlaybackFrame frame(long sessionId, Instant at) {
		RaceReplay replay = replay(sessionId);
		TrackOutline outline = replay.outline();
		var frame = PlaybackFrame.at(at, replay.totalLaps());
		for (int car : replay.carNumbers()) {
			ProgressTimeline timeline = replay.timeline(car);
			double lapsDone = timeline.lapsDoneAt(at);
			Status status = status(replay, car, timeline, at);
			Instant reached = status == Status.FINISHED || status == Status.RETIRED ? timeline.lastAt()
					: timeline.reachedAt(lapsDone);
			var point = outline == null ? new TrackOutline.Point(0, 0) : outline.pointAt(lapsDone);
			frame.car(new PlaybackFrame.CarState(car, lapsDone, reached, status, point.x(), point.y()));
		}
		return frame.build();
	}

	public ReplayResponse replayResponse(long sessionId) {
		RaceReplay replay = replay(sessionId);
		long start = replay.clockStart().toEpochMilli();
		var timing = replay.carNumbers().stream().map(car -> {
			ProgressTimeline timeline = replay.timeline(car);
			var points = timeline.points();
			long[] t = points.stream().mapToLong(p -> p.at().toEpochMilli() - start).toArray();
			double[] lapsDone = points.stream().mapToDouble(p -> Math.round(p.lapsDone() * 10_000) / 10_000.0).toArray();
			var pits = replay.pits().getOrDefault(car, List.of()).stream()
					.map(p -> new ReplayResponse.PitWindow(p.lap(), p.from().toEpochMilli() - start,
							p.to().toEpochMilli() - start))
					.toList();
			boolean finished = !timeline.lastAt().isBefore(replay.chequeredAt());
			return new ReplayResponse.CarTiming(car, t, lapsDone, pits, finished,
					finished ? null : timeline.lastAt().toEpochMilli() - start);
		}).toList();
		var driversOut = replay.drivers().stream()
				.map(d -> new ReplayResponse.Driver(d.number(), d.code(), d.name(), d.team(), d.teamColour()))
				.toList();
		return new ReplayResponse(replay.sessionId(), replay.raceId(), replay.sessionName(), replay.clockStart(),
				replay.chequeredAt(), replay.totalLaps(), replay.layout() == null ? null : LayoutResponse.of(replay.layout()),
				driversOut, timing);
	}

	private static Status status(RaceReplay replay, int car, ProgressTimeline timeline, Instant at) {
		if (at.isBefore(timeline.firstAt())) {
			return Status.ON_GRID;
		}
		if (!at.isBefore(timeline.lastAt())) {
			// stopped: a car that stopped before the flag retired; one that took the flag finished
			return timeline.lastAt().isBefore(replay.chequeredAt()) ? Status.RETIRED : Status.FINISHED;
		}
		boolean inPit = replay.pits().getOrDefault(car, List.of()).stream()
				.anyMatch(p -> !at.isBefore(p.from()) && at.isBefore(p.to()));
		return inPit ? Status.IN_PIT : Status.RUNNING;
	}

	private static double[] sectorEnds(CircuitLayout layout, List<Lap> sessionLaps) {
		if (layout != null && layout.sectorEnds() != null) {
			return layout.sectorEnds();
		}
		double[] estimate = TrackOutline.estimateSectorEnds(sessionLaps.stream()
				.filter(Lap::isComplete)
				.filter(l -> l.getLapMs() != null && l.getLapMs() > 0)
				.map(l -> new int[] { l.getSector1Ms(), l.getSector2Ms(), l.getLapMs() })
				.toList());
		return estimate != null ? estimate : EVEN_SECTORS;
	}

}

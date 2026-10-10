package com.telemetrix.telemetry;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.race.Session;
import com.telemetrix.race.SessionRepository;
import com.telemetrix.telemetry.LapAnalysis.EstimatesResponse;
import com.telemetrix.telemetry.LapAnalysis.Tyre;
import com.telemetrix.timing.Lap;
import com.telemetrix.timing.LapRepository;
import com.telemetrix.timing.TireStintRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

// The telemetry view's analysis of a lap (and of two laps compared): the lap's telemetry — downloaded
// on demand (TelemetryService) — against distance, with its sector times, tyres and estimates.
@Service
public class LapAnalysisService {

	private final SessionRepository sessions;

	private final LapRepository laps;

	private final TireStintRepository stints;

	private final TelemetryService telemetry;

	LapAnalysisService(SessionRepository sessions, LapRepository laps, TireStintRepository stints,
			TelemetryService telemetry) {
		this.sessions = sessions;
		this.laps = laps;
		this.stints = stints;
		this.telemetry = telemetry;
	}

	/** One lap of a car; `lap` null: its fastest complete lap. */
	public LapAnalysis lap(long sessionId, int driver, Integer lap) {
		Session session = sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("Session", sessionId));
		List<Lap> sessionLaps = laps.findBySessionOrderByDriverNumberAscLapNumberAsc(session);
		Lap theLap = sessionLaps.stream()
				.filter(l -> l.getDriverNumber() == driver)
				.filter(l -> lap == null ? l.isComplete() && l.getLapMs() != null : l.getLapNumber() == lap)
				.filter(l -> l.getStartsAt() != null && l.getLapMs() != null)
				.min(Comparator.comparing(Lap::getLapMs))
				.orElseThrow(() -> new NotFoundException(lap == null ? "A timed lap of car" : "Lap " + lap + " of car",
						driver));
		int totalLaps = sessionLaps.stream().mapToInt(Lap::getLapNumber).max().orElse(0);

		List<LapTrace.Sample> samples = telemetry.telemetry(sessionId, driver, theLap.getLapNumber()).points().stream()
				.map(p -> new LapTrace.Sample(p.t(), p.speed(), p.throttle(), p.brake(), p.gear(), p.rpm(), p.drs()))
				.toList();
		LapTrace trace = LapTrace.of(samples, theLap.getStartsAt(), theLap.getLapMs());
		Estimates.Ers ers = Estimates.ers(samples);
		var estimates = new EstimatesResponse(round(Estimates.fuelKg(theLap.getLapNumber(), totalLaps), 10),
				round(ers.deployedMj(), 100), round(ers.harvestedMj(), 100), Estimates.NOTE);

		return new LapAnalysis(sessionId, driver, theLap.getLapNumber(), theLap.getStartsAt(), theLap.getLapMs() / 1000.0,
				seconds(sectors(theLap)), tyre(session, driver, theLap.getLapNumber()), estimates,
				round(trace.distance(), 10), trace.points().stream()
						.map(p -> new LapTrace.Point(p.time(), round(p.distance(), 10), p.speed(), p.throttle(), p.brake(),
								p.gear(), p.rpm(), p.drs()))
						.toList());
	}

	/** Two laps compared, by how far round the lap each is; null laps: each driver's fastest. */
	public LapAnalysis.Comparison compare(long sessionId, int driver, Integer lap, int otherDriver, Integer otherLap) {
		LapAnalysis reference = lap(sessionId, driver, lap);
		LapAnalysis other = lap(sessionId, otherDriver, otherLap);
		List<LapTrace.DeltaPoint> delta = LapTrace.delta(asTrace(reference), asTrace(other)).stream()
				.map(d -> new LapTrace.DeltaPoint(d.distance(), Math.round(d.delta() * 1000) / 1000.0))
				.toList();
		Session session = sessions.findById(sessionId).orElseThrow();
		Integer[] referenceSectors = sectors(lapOf(session, driver, reference.lap()));
		Integer[] otherSectors = sectors(lapOf(session, otherDriver, other.lap()));
		return new LapAnalysis.Comparison(reference, other,
				Math.round((other.lapTime() - reference.lapTime()) * 1000) / 1000.0,
				LapTrace.sectorDeltas(referenceSectors, otherSectors), delta);
	}

	private Lap lapOf(Session session, int driver, int lap) {
		return laps.findBySessionAndDriverNumberAndLapNumber(session, (short) driver, (short) lap).orElseThrow();
	}

	private Tyre tyre(Session session, int driver, int lap) {
		return stints.findBySessionOrderByDriverNumberAsc(session).stream()
				.filter(s -> s.getDriverNumber() == driver && s.getLapStart() != null && s.getLapStart() <= lap
						&& (s.getLapEnd() == null || s.getLapEnd() >= lap))
				.findFirst()
				.map(s -> new Tyre(s.getCompound(), s.getStintNumber(),
						lap - s.getLapStart() + (s.getTyreAgeAtStart() == null ? 0 : s.getTyreAgeAtStart())))
				.orElse(null);
	}

	private static LapTrace asTrace(LapAnalysis lap) {
		return new LapTrace(lap.trace(), lap.distance());
	}

	private static Integer[] sectors(Lap lap) {
		return new Integer[] { lap.getSector1Ms(), lap.getSector2Ms(), lap.getSector3Ms() };
	}

	private static Double[] seconds(Integer[] ms) {
		Double[] s = new Double[ms.length];
		for (int i = 0; i < ms.length; i++) {
			s[i] = ms[i] == null ? null : ms[i] / 1000.0;
		}
		return s;
	}

	private static double round(double value, int factor) {
		return Math.round(value * factor) / (double) factor;
	}

}

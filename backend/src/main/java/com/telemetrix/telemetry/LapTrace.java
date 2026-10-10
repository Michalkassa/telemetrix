package com.telemetrix.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// One lap of telemetry against distance: `time` is ms since the lap started, `distance` metres from the
// line, integrated from speed (as FastF1 does; positions are too coarse). The lap is pinned at both
// ends — time 0 at its start, its lap time at its end — so two laps compared by how far round they are
// differ, at the end, by exactly their lap-time difference.
public record LapTrace(List<Point> points, double distance) {

	/** A telemetry sample: km/h, % throttle, brake (0/100), gear, rpm, DRS code. */
	public record Sample(Instant at, Integer speed, Integer throttle, Integer brake, Integer gear, Integer rpm,
			Integer drs) {
	}

	public record Point(long time, double distance, Integer speed, Integer throttle, Integer brake, Integer gear,
			Integer rpm, Integer drs) {
	}

	/** At `distance` m round the reference lap, the other lap is `delta` s behind (negative: ahead). */
	public record DeltaPoint(double distance, double delta) {
	}

	public static LapTrace of(List<Sample> samples, Instant lapStart, int lapMs) {
		List<Sample> sorted = samples.stream().filter(s -> s.at() != null).sorted(Comparator.comparing(Sample::at))
				.toList();
		List<Point> points = new ArrayList<>();
		if (sorted.isEmpty()) {
			return new LapTrace(points, 0);
		}
		points.add(at(sorted, lapStart, 0));
		for (Sample s : sorted) {
			long ms = Duration.between(lapStart, s.at()).toMillis();
			if (ms > 0 && ms < lapMs) {
				points.add(point(s, ms));
			}
		}
		points.add(at(sorted, lapStart, lapMs));

		// distance: the area under the speed trace (trapezoids), km/h → m/s
		List<Point> measured = new ArrayList<>(points.size());
		double distance = 0;
		Point previous = null;
		for (Point p : points) {
			if (previous != null) {
				double seconds = (p.time() - previous.time()) / 1000.0;
				distance += (speed(previous) + speed(p)) / 2 / 3.6 * seconds;
			}
			measured.add(new Point(p.time(), distance, p.speed(), p.throttle(), p.brake(), p.gear(), p.rpm(), p.drs()));
			previous = p;
		}
		return new LapTrace(List.copyOf(measured), distance);
	}

	/** The other lap's time minus the reference's, at each point of the reference lap, by share of the lap. */
	public static List<DeltaPoint> delta(LapTrace reference, LapTrace other) {
		List<DeltaPoint> delta = new ArrayList<>();
		if (reference.points().isEmpty() || other.points().isEmpty() || reference.distance() <= 0
				|| other.distance() <= 0) {
			return delta;
		}
		for (Point p : reference.points()) {
			double share = p.distance() / reference.distance();
			double otherTime = other.timeAt(share * other.distance());
			delta.add(new DeltaPoint(p.distance(), (otherTime - p.time()) / 1000.0));
		}
		return delta;
	}

	/** Each sector's time difference in seconds (other − reference), to the millisecond; null if one is missing. */
	public static Double[] sectorDeltas(Integer[] reference, Integer[] other) {
		Double[] deltas = new Double[3];
		for (int i = 0; i < 3; i++) {
			if (reference[i] != null && other[i] != null) {
				deltas[i] = (other[i] - reference[i]) / 1000.0;
			}
		}
		return deltas;
	}

	// ms since the lap started when the car had covered `metres` (linear between points)
	double timeAt(double metres) {
		Point before = points.getFirst();
		for (Point p : points) {
			if (p.distance() >= metres) {
				double span = p.distance() - before.distance();
				double share = span <= 0 ? 0 : (metres - before.distance()) / span;
				return before.time() + share * (p.time() - before.time());
			}
			before = p;
		}
		return points.getLast().time();
	}

	// the car at `ms` into the lap: speed interpolated, the rest as last sampled
	private static Point at(List<Sample> sorted, Instant lapStart, long ms) {
		Instant when = lapStart.plusMillis(ms);
		Sample before = null;
		Sample after = null;
		for (Sample s : sorted) {
			if (!s.at().isAfter(when)) {
				before = s;
			}
			else {
				after = s;
				break;
			}
		}
		if (before == null) {
			return point(after, ms);
		}
		if (after == null || before.at().equals(when) || before.speed() == null || after.speed() == null) {
			return point(before, ms);
		}
		double share = (double) Duration.between(before.at(), when).toMillis()
				/ Duration.between(before.at(), after.at()).toMillis();
		int speed = (int) Math.round(before.speed() + share * (after.speed() - before.speed()));
		return new Point(ms, 0, speed, before.throttle(), before.brake(), before.gear(), before.rpm(), before.drs());
	}

	private static Point point(Sample s, long ms) {
		return new Point(ms, 0, s.speed(), s.throttle(), s.brake(), s.gear(), s.rpm(), s.drs());
	}

	private static double speed(Point p) {
		return p.speed() == null ? 0 : p.speed();
	}

}

package com.telemetrix.replay;

import com.telemetrix.timing.Lap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// One car's progress through a race (docs/adr/0004) as "laps done": 9.25 is a quarter of the way
// round lap 10. Built from timing points — each complete lap gives 4: its start, the ends of sectors
// 1 and 2 (at their share of the lap distance), and its end — and interpolated linearly in time
// between them. Before the first point the car is on the grid (0); after the last it stays there.
public final class ProgressTimeline {

	public record TimingPoint(Instant at, double lapsDone) {
	}

	private final List<TimingPoint> points;

	private final long[] millis;

	private final double[] lapsDone;

	private ProgressTimeline(List<TimingPoint> points) {
		this.points = List.copyOf(points);
		this.millis = points.stream().mapToLong(p -> p.at().toEpochMilli()).toArray();
		this.lapsDone = points.stream().mapToDouble(TimingPoint::lapsDone).toArray();
	}

	/** `sector1End` and `sector2End`: where the sectors end, as a share of the lap distance. */
	public static ProgressTimeline fromLaps(List<Lap> laps, double sector1End, double sector2End) {
		List<TimingPoint> raw = new ArrayList<>();
		for (Lap lap : laps) {
			Instant start = lap.getStartsAt();
			if (start == null) {
				continue;
			}
			int before = lap.getLapNumber() - 1;
			raw.add(new TimingPoint(start, before));
			if (lap.isComplete()) {
				raw.add(new TimingPoint(start.plusMillis(lap.getSector1Ms()), before + sector1End));
				raw.add(new TimingPoint(start.plusMillis(lap.getSector1Ms() + lap.getSector2Ms()), before + sector2End));
				int duration = lap.getLapMs() != null ? lap.getLapMs()
						: lap.getSector1Ms() + lap.getSector2Ms() + lap.getSector3Ms();
				raw.add(new TimingPoint(start.plusMillis(duration), before + 1));
			}
			else if (lap.getLapMs() != null) { // a sector is missing: interpolate across the whole lap
				raw.add(new TimingPoint(start.plusMillis(lap.getLapMs()), before + 1));
			}
		}
		raw.sort(Comparator.comparing(TimingPoint::at));

		// One point per moment, and never backwards: a point behind one already reached is bad data.
		List<TimingPoint> clean = new ArrayList<>();
		for (TimingPoint p : raw) {
			if (clean.isEmpty()) {
				clean.add(p);
				continue;
			}
			TimingPoint last = clean.getLast();
			if (p.lapsDone() < last.lapsDone()) {
				continue;
			}
			if (p.at().equals(last.at())) {
				clean.set(clean.size() - 1, p); // same moment: keep the further one
			}
			else {
				clean.add(p);
			}
		}
		return new ProgressTimeline(clean);
	}

	public List<TimingPoint> points() {
		return points;
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}

	public Instant firstAt() {
		return points.isEmpty() ? null : points.getFirst().at();
	}

	public Instant lastAt() {
		return points.isEmpty() ? null : points.getLast().at();
	}

	public double finalLapsDone() {
		return points.isEmpty() ? 0 : points.getLast().lapsDone();
	}

	public double lapsDoneAt(Instant at) {
		if (points.isEmpty()) {
			return 0;
		}
		long t = at.toEpochMilli();
		if (t <= millis[0]) {
			return t == millis[0] ? lapsDone[0] : 0;
		}
		if (t >= millis[millis.length - 1]) {
			return lapsDone[lapsDone.length - 1];
		}
		int i = java.util.Arrays.binarySearch(millis, t);
		if (i >= 0) {
			return lapsDone[i];
		}
		int after = -i - 1;
		int before = after - 1;
		double share = (double) (t - millis[before]) / (millis[after] - millis[before]);
		return lapsDone[before] + share * (lapsDone[after] - lapsDone[before]);
	}

	/** When the car first got `target` laps done (e.g. 10.0: completed lap 10); null if it never did. */
	public Instant reachedAt(double target) {
		for (int i = 0; i < lapsDone.length; i++) {
			if (lapsDone[i] >= target) {
				if (i == 0 || lapsDone[i] == lapsDone[i - 1]) {
					return points.get(i).at();
				}
				double share = (target - lapsDone[i - 1]) / (lapsDone[i] - lapsDone[i - 1]);
				return Instant.ofEpochMilli(millis[i - 1] + Math.round(share * (millis[i] - millis[i - 1])));
			}
		}
		return null;
	}

}

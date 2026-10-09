package com.telemetrix.telemetry;

import com.telemetrix.ingestion.openf1.OpenF1CarData;
import com.telemetrix.ingestion.openf1.OpenF1Location;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// Turns OpenF1's two separate feeds for one car (car data, location) into one checked series:
// - samples outside the session window, and repeats of a timestamp, are dropped (and counted);
// - each car-data sample gets the nearest position within 0.5 s; (0, 0, 0) is "no position";
// - gaps of more than 2 s are recorded, never filled in.
final class TelemetryMerger {

	static final Duration MAX_POSITION_DISTANCE = Duration.ofMillis(500);

	static final Duration GAP = Duration.ofSeconds(2);

	record Result(List<TelemetryPoint> points, List<TelemetryGap> gaps, int dropped) {
	}

	private TelemetryMerger() {
	}

	static Result merge(List<OpenF1CarData> carData, List<OpenF1Location> locations, Instant from, Instant to) {
		var samples = carData.stream()
				.filter(s -> s.date() != null)
				.sorted(Comparator.comparing(OpenF1CarData::date))
				.toList();
		int dropped = carData.size() - samples.size();

		var positions = locations.stream()
				.filter(l -> l.date() != null)
				.sorted(Comparator.comparing(OpenF1Location::date))
				.toList();
		Instant[] positionTimes = positions.stream().map(l -> l.date().toInstant()).toArray(Instant[]::new);

		List<TelemetryPoint> points = new ArrayList<>();
		Instant previous = null;
		for (OpenF1CarData sample : samples) {
			Instant at = sample.date().toInstant();
			if (at.isBefore(from) || at.isAfter(to) || at.equals(previous)) {
				dropped++;
				continue;
			}
			OpenF1Location position = nearest(positions, positionTimes, at);
			points.add(new TelemetryPoint(at, sample.speed(), sample.throttle(), sample.brake(), sample.gear(),
					sample.rpm(), sample.drs(), position == null ? null : position.x(),
					position == null ? null : position.y(), position == null ? null : position.z()));
			previous = at;
		}

		List<TelemetryGap> gaps = new ArrayList<>();
		for (int i = 1; i < points.size(); i++) {
			Instant before = points.get(i - 1).recordedAt();
			Instant after = points.get(i).recordedAt();
			if (Duration.between(before, after).compareTo(GAP) > 0) {
				gaps.add(new TelemetryGap(before, after));
			}
		}
		return new Result(points, gaps, dropped);
	}

	private static OpenF1Location nearest(List<OpenF1Location> positions, Instant[] times, Instant at) {
		int i = Arrays.binarySearch(times, at);
		int insertion = i >= 0 ? i : -i - 1;
		OpenF1Location best = null;
		Duration bestDistance = null;
		for (int j = insertion - 1; j <= insertion; j++) {
			if (j < 0 || j >= times.length) {
				continue;
			}
			Duration distance = Duration.between(times[j], at).abs();
			if (distance.compareTo(MAX_POSITION_DISTANCE) <= 0 && (bestDistance == null || distance.compareTo(bestDistance) < 0)) {
				best = positions.get(j);
				bestDistance = distance;
			}
		}
		if (best == null || isNowhere(best)) {
			return null;
		}
		return best;
	}

	private static boolean isNowhere(OpenF1Location l) {
		return l.x() == null || l.y() == null || (l.x() == 0 && l.y() == 0 && (l.z() == null || l.z() == 0));
	}

}

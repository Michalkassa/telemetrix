package com.telemetrix.circuit;

import java.util.Arrays;
import java.util.List;

// A circuit's outline as a closed loop, measured by distance. Lap progress (0 = start/finish line,
// 0.5 = halfway round by distance, 1 = back at the line) becomes a point on the map: pointAt().
// The raw points are in OpenF1's coordinates; points() and pointAt() are rotated for display.
public final class TrackOutline {

	public record Point(double x, double y) {
	}

	public record BoundingBox(double minX, double minY, double maxX, double maxY) {
	}

	private final List<Point> raw;

	private final List<Point> points;

	private final double[] distanceAt; // distance from the start to each point

	private final double length; // including the closing stretch back to the start

	public TrackOutline(List<Point> rawPoints, double rotationDegrees) {
		if (rawPoints.size() < 3) {
			throw new IllegalArgumentException("A track outline needs at least 3 points, got " + rawPoints.size());
		}
		this.raw = List.copyOf(rawPoints);
		this.points = rawPoints.stream().map(p -> rotate(p, rotationDegrees)).toList();
		this.distanceAt = new double[points.size()];
		for (int i = 1; i < points.size(); i++) {
			distanceAt[i] = distanceAt[i - 1] + distance(points.get(i - 1), points.get(i));
		}
		this.length = distanceAt[points.size() - 1] + distance(points.getLast(), points.getFirst());
	}

	/** The point at a share of the lap distance; whole laps are ignored (1.25 is 0.25). */
	public Point pointAt(double progress) {
		double wanted = (progress - Math.floor(progress)) * length;
		int i = Arrays.binarySearch(distanceAt, wanted);
		if (i < 0) {
			i = -i - 2; // the last point before `wanted`
		}
		Point from = points.get(i);
		Point to = points.get((i + 1) % points.size());
		double segment = (i == points.size() - 1 ? length : distanceAt[i + 1]) - distanceAt[i];
		double t = segment == 0 ? 0 : (wanted - distanceAt[i]) / segment;
		return new Point(from.x() + t * (to.x() - from.x()), from.y() + t * (to.y() - from.y()));
	}

	/** The share of the lap distance at which point `index` lies. */
	public double progressAtIndex(int index) {
		return distanceAt[index] / length;
	}

	/** The outline point closest to a raw (unrotated) position. */
	public int nearestIndex(Point rawPosition) {
		int best = 0;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < raw.size(); i++) {
			double d = distance(raw.get(i), rawPosition);
			if (d < bestDistance) {
				bestDistance = d;
				best = i;
			}
		}
		return best;
	}

	/**
	 * Where sectors 1 and 2 end, from the track's mini-sectors: `miniSectorEnds` are the outline indexes
	 * where each mini-sector ends; sector 1 has `sector1Count` of them, sector 2 `sector2Count`.
	 * Null when the counts don't fit.
	 */
	public double[] sectorEndsFromMiniSectors(List<Integer> miniSectorEnds, int sector1Count, int sector2Count) {
		int sector2Last = sector1Count + sector2Count - 1;
		if (sector1Count < 1 || sector2Count < 1 || miniSectorEnds == null || sector2Last >= miniSectorEnds.size()) {
			return null;
		}
		int s1 = miniSectorEnds.get(sector1Count - 1);
		int s2 = miniSectorEnds.get(sector2Last);
		if (s1 >= raw.size() || s2 >= raw.size()) {
			return null;
		}
		return new double[] { progressAtIndex(s1), progressAtIndex(s2) };
	}

	/** Where sectors 1 and 2 end, estimated as the median share of the lap time: (s1, s2, lap) in ms. */
	public static double[] estimateSectorEnds(List<int[]> sectorTimes) {
		if (sectorTimes.isEmpty()) {
			return null;
		}
		return new double[] { median(sectorTimes.stream().mapToDouble(t -> (double) t[0] / t[2]).toArray()),
				median(sectorTimes.stream().mapToDouble(t -> (double) (t[0] + t[1]) / t[2]).toArray()) };
	}

	public List<Point> points() {
		return points;
	}

	public double length() {
		return length;
	}

	public BoundingBox boundingBox() {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (Point p : points) {
			minX = Math.min(minX, p.x());
			minY = Math.min(minY, p.y());
			maxX = Math.max(maxX, p.x());
			maxY = Math.max(maxY, p.y());
		}
		return new BoundingBox(minX, minY, maxX, maxY);
	}

	/** Rotates counter-clockwise around the origin. */
	public static Point rotate(Point p, double degrees) {
		if (degrees == 0) {
			return p;
		}
		double a = Math.toRadians(degrees);
		return new Point(p.x() * Math.cos(a) - p.y() * Math.sin(a), p.x() * Math.sin(a) + p.y() * Math.cos(a));
	}

	private static double distance(Point a, Point b) {
		return Math.hypot(b.x() - a.x(), b.y() - a.y());
	}

	private static double median(double[] values) {
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		int n = sorted.length;
		return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
	}

}

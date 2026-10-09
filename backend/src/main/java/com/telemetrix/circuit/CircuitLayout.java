package com.telemetrix.circuit;

import com.telemetrix.circuit.TrackOutline.Point;
import java.util.List;

// A circuit's track map for one year: raw outline points (OpenF1 coordinates, starting at the
// start/finish line), the rotation to draw it upright, where sectors 1 and 2 end (share of the lap
// distance), and the corners.
public record CircuitLayout(String circuitRef, int year, Source source, double rotation, List<Point> points,
		double[] sectorEnds, SectorEndsSource sectorEndsSource, List<Corner> corners) {

	public enum Source {
		/** Multiviewer's circuit data. */
		MULTIVIEWER,
		/** Built from one lap of OpenF1 positions, when Multiviewer has none. */
		OPENF1_LAP
	}

	public enum SectorEndsSource {
		/** From the track's mini-sectors: exact. */
		TRACK,
		/** From median sector / lap time ratios: close. */
		ESTIMATED
	}

	/** A numbered corner at a raw position, `progress` round the lap. */
	public record Corner(int number, double x, double y, double progress) {
	}

	public TrackOutline outline() {
		return new TrackOutline(points, rotation);
	}

}

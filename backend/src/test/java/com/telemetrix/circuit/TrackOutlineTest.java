package com.telemetrix.circuit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.telemetrix.circuit.TrackOutline.Point;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// Geometry of the track map, on synthetic shapes where the answers are known.
class TrackOutlineTest {

	// a 100 x 100 square, 400 round, with extra points on its first side only
	static final List<Point> UNEVEN_SQUARE = List.of(new Point(0, 0), new Point(25, 0), new Point(50, 0),
			new Point(75, 0), new Point(100, 0), new Point(100, 100), new Point(0, 100));

	TrackOutline square = new TrackOutline(UNEVEN_SQUARE, 0);

	@Test
	void theLapIsAClosedLoopMeasuredByDistance() {
		assertThat(square.length()).isEqualTo(400.0);
		assertThat(square.pointAt(0)).isEqualTo(new Point(0, 0));
		assertThat(square.pointAt(1)).isEqualTo(square.pointAt(0));
		assertThat(square.pointAt(0.125)).isEqualTo(new Point(50, 0));
		// halfway by distance is the far corner; halfway by point index would be (75, 0)
		assertThat(square.pointAt(0.5)).isEqualTo(new Point(100, 100));
		assertThat(square.pointAt(0.875)).isEqualTo(new Point(0, 50)); // on the closing side
		assertThat(square.pointAt(1.25)).isEqualTo(square.pointAt(0.25)); // the next lap
	}

	@Test
	void eachPointHasItsShareOfTheLap() {
		assertThat(square.progressAtIndex(0)).isEqualTo(0.0);
		assertThat(square.progressAtIndex(4)).isEqualTo(0.25);
		assertThat(square.progressAtIndex(6)).isEqualTo(0.75);
	}

	@Test
	void rotationTurnsTheMapCounterClockwise() {
		var rotated = new TrackOutline(UNEVEN_SQUARE, 90);

		assertThat(rotated.pointAt(0.25).x()).isCloseTo(0, within(1e-9));
		assertThat(rotated.pointAt(0.25).y()).isCloseTo(100, within(1e-9));
		assertThat(rotated.length()).isCloseTo(400, within(1e-9));
		var box = rotated.boundingBox();
		assertThat(box.minX()).isCloseTo(-100, within(1e-9));
		assertThat(box.maxX()).isCloseTo(0, within(1e-9));
		assertThat(box.minY()).isCloseTo(0, within(1e-9));
		assertThat(box.maxY()).isCloseTo(100, within(1e-9));
	}

	@Test
	void aCircleIsMeasuredCorrectly() {
		List<Point> circle = new ArrayList<>();
		for (int i = 0; i < 360; i++) {
			double a = Math.toRadians(i);
			circle.add(new Point(1000 * Math.cos(a), 1000 * Math.sin(a)));
		}
		var outline = new TrackOutline(circle, 0);

		assertThat(outline.length()).isCloseTo(2 * Math.PI * 1000, within(1.0));
		assertThat(outline.pointAt(0.25).x()).isCloseTo(0, within(1.0));
		assertThat(outline.pointAt(0.25).y()).isCloseTo(1000, within(1.0));
	}

	@Test
	void sectorEndsComeFromTheMiniSectorsOfEachSector() {
		// mini-sectors end at points 2, 4, 5 and 6; sector 1 has 2 of them, sector 2 has 1
		var ends = square.sectorEndsFromMiniSectors(List.of(2, 4, 5, 6), 2, 1);

		assertThat(ends).containsExactly(0.25, 0.5);
	}

	@Test
	void sectorEndsCannotComeFromMiniSectorsThatDoNotFit() {
		assertThat(square.sectorEndsFromMiniSectors(List.of(2, 4), 2, 1)).isNull();
	}

	@Test
	void sectorEndsCanBeEstimatedFromSectorTimes() {
		// (sector 1, sector 2, lap) in ms: medians of 30/95, 31/96, 29/95 and of 70/95, 71/96, 70/95
		var ends = TrackOutline.estimateSectorEnds(List.of(new int[] { 30_000, 40_000, 95_000 },
				new int[] { 31_000, 40_000, 96_000 }, new int[] { 29_000, 41_000, 95_000 }));

		assertThat(ends[0]).isCloseTo(30.0 / 95, within(1e-9));
		assertThat(ends[1]).isCloseTo(70.0 / 95, within(1e-9));
	}

	@Test
	void theNearestPointOfTheOutlineIsFound() {
		assertThat(square.nearestIndex(new Point(98, 3))).isEqualTo(4);
	}

}

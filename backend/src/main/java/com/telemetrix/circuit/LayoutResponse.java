package com.telemetrix.circuit;

import com.telemetrix.circuit.TrackOutline.BoundingBox;
import java.util.Arrays;
import java.util.List;

// A track map ready to draw: points and corners already rotated for display, rounded to 0.1.
// Telemetry x/y (raw coordinates) must be rotated by `rotation` to sit on it.
public record LayoutResponse(String circuit, int year, String source, double rotation, double length,
		BoundingBox boundingBox, List<double[]> points, double[] startFinish, List<Double> sectorEnds,
		String sectorEndsSource, List<CornerResponse> corners) {

	public record CornerResponse(int number, double x, double y, double progress) {
	}

	public static LayoutResponse of(CircuitLayout layout) {
		TrackOutline outline = layout.outline();
		var box = outline.boundingBox();
		var points = outline.points().stream().map(p -> new double[] { round(p.x()), round(p.y()) }).toList();
		var corners = layout.corners().stream().map(c -> {
			var at = TrackOutline.rotate(new TrackOutline.Point(c.x(), c.y()), layout.rotation());
			return new CornerResponse(c.number(), round(at.x()), round(at.y()), c.progress());
		}).toList();
		return new LayoutResponse(layout.circuitRef(), layout.year(), layout.source().name(), layout.rotation(),
				round(outline.length()), new BoundingBox(round(box.minX()), round(box.minY()), round(box.maxX()),
						round(box.maxY())),
				points, points.getFirst(),
				layout.sectorEnds() == null ? null : Arrays.stream(layout.sectorEnds()).boxed().toList(),
				layout.sectorEndsSource() == null ? null : layout.sectorEndsSource().name(), corners);
	}

	// one decimal; + 0.0 turns -0.0 into 0.0
	static double round(double value) {
		return Math.round(value * 10) / 10.0 + 0.0;
	}

}

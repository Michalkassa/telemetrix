package com.telemetrix.circuit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.circuit.TrackOutline.Point;
import com.telemetrix.common.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(CircuitLayoutController.class)
class CircuitLayoutControllerTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	CircuitLayoutService service;

	@Test
	void returnsTheMapReadyToDraw() {
		var layout = new CircuitLayout("bahrain", 2024, CircuitLayout.Source.MULTIVIEWER, 90,
				List.of(new Point(0, 0), new Point(100, 0), new Point(100, 100), new Point(0, 100)),
				new double[] { 0.25, 0.75 }, CircuitLayout.SectorEndsSource.TRACK,
				List.of(new CircuitLayout.Corner(1, 100, 0, 0.25)),
				List.of(new CircuitLayout.MarshalSector(1, 0.0), new CircuitLayout.MarshalSector(2, 0.5)),
				List.of(new Point(10, 0), new Point(20, 0)));
		given(service.layout("bahrain", 2024)).willReturn(layout);

		// rotated 90 degrees counter-clockwise for display: (x, y) -> (-y, x)
		assertThat(mvc.get().uri("/api/circuits/bahrain/layout?year=2024")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"circuit": "bahrain", "year": 2024, "source": "MULTIVIEWER", "rotation": 90.0, "length": 400.0,
				 "boundingBox": {"minX": -100.0, "minY": 0.0, "maxX": 0.0, "maxY": 100.0},
				 "points": [[0.0, 0.0], [0.0, 100.0], [-100.0, 100.0], [-100.0, 0.0]],
				 "startFinish": [0.0, 0.0],
				 "sectorEnds": [0.25, 0.75], "sectorEndsSource": "TRACK",
				 "corners": [{"number": 1, "x": 0.0, "y": 100.0, "progress": 0.25}],
				 "marshalSectors": [{"number": 1, "progress": 0.0}, {"number": 2, "progress": 0.5}],
				 "pitLane": [[0.0, 10.0], [0.0, 20.0]]}
				""");
	}

	@Test
	void anUnknownLayoutIs404() {
		given(service.layout("nowhere", 2024)).willThrow(new NotFoundException("Layout of circuit nowhere in", 2024));

		assertThat(mvc.get().uri("/api/circuits/nowhere/layout?year=2024")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void theYearIsRequired() {
		assertThat(mvc.get().uri("/api/circuits/bahrain/layout")).hasStatus(HttpStatus.BAD_REQUEST);
	}

}

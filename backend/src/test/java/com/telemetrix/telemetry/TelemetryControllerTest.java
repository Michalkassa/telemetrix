package com.telemetrix.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.common.NotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(TelemetryController.class)
// controller behaviour only: who may call it is AuthorizationMatrixTest's, against the real filter chain
@AutoConfigureMockMvc(addFilters = false)
class TelemetryControllerTest {

	static final Instant T = Instant.parse("2024-03-02T15:18:16.271Z");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	TelemetryService service;

	@MockitoBean
	LapAnalysisService analysis;

	@Test
	void returnsOneCarsLap() {
		given(service.telemetry(5, 1, 10)).willReturn(new TelemetryResponse(5, 1, 10,
				Instant.parse("2024-03-02T15:18:15.404Z"), Instant.parse("2024-03-02T15:19:52.364Z"),
				List.of(new TelemetryResponse.Point(T, 286, 100, 0, 7, 11234, 12, -343, 2113, 0)), List.of()));

		assertThat(mvc.get().uri("/api/sessions/5/telemetry?driver=1&lap=10")).hasStatusOk().bodyJson()
				.isStrictlyEqualTo("""
						{"sessionId": 5, "driver": 1, "lap": 10,
						 "from": "2024-03-02T15:18:15.404Z", "to": "2024-03-02T15:19:52.364Z",
						 "points": [{"t": "2024-03-02T15:18:16.271Z", "speed": 286, "throttle": 100, "brake": 0,
						             "gear": 7, "rpm": 11234, "drs": 12, "x": -343, "y": 2113, "z": 0}],
						 "gaps": []}
						""");
	}

	@Test
	void theDriverIsRequired() {
		assertThat(mvc.get().uri("/api/sessions/5/telemetry")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void anUnknownLapIs404() {
		given(service.telemetry(5, 1, 99)).willThrow(new NotFoundException("Lap 99 of car", 1));

		assertThat(mvc.get().uri("/api/sessions/5/telemetry?driver=1&lap=99")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void aComparisonNeedsTheSecondDriver() {
		assertThat(mvc.get().uri("/api/sessions/5/telemetry/compare?driver=1")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void aLapAnalysisIsForOneDriver() {
		assertThat(mvc.get().uri("/api/sessions/5/telemetry/lap?driver=100")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/sessions/5/telemetry/lap")).hasStatus(HttpStatus.BAD_REQUEST);
	}

}

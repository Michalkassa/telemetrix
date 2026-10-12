package com.telemetrix.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.replay.PlaybackFrame.Status;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ReplayController.class)
// controller behaviour only: who may call it is AuthorizationMatrixTest's, against the real filter chain
@AutoConfigureMockMvc(addFilters = false)
class ReplayControllerTest {

	static final Instant START = Instant.parse("2024-03-02T15:03:42.341Z");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	ReplayService service;

	@Test
	void returnsTheWholeRaceInOneResponse() {
		given(service.replayResponse(5)).willReturn(new ReplayResponse(5, 1, "Race", START,
				Instant.parse("2024-03-02T16:35:27.617Z"), 57, null,
				List.of(new ReplayResponse.Driver(1, "VER", "Max VERSTAPPEN", "Red Bull Racing", "3671c6")),
				List.of(new ReplayResponse.CarTiming(1, new long[] { 0, 32877 }, new double[] { 0, 0.3279 },
						List.of(new ReplayResponse.PitWindow(17, 1_000_000, 1_022_000)), true, null)),
				List.of(new ReplayResponse.Weather(-60_000, 30.0, 38.3, 68.0, false, 0.8))));

		assertThat(mvc.get().uri("/api/sessions/5/replay")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"sessionId": 5, "raceId": 1, "session": "Race", "clockStart": "2024-03-02T15:03:42.341Z",
				 "chequeredAt": "2024-03-02T16:35:27.617Z", "totalLaps": 57, "layout": null,
				 "drivers": [{"number": 1, "code": "VER", "name": "Max VERSTAPPEN", "team": "Red Bull Racing",
				              "teamColour": "3671c6"}],
				 "timing": [{"number": 1, "t": [0, 32877], "laps": [0.0, 0.3279],
				             "pits": [{"lap": 17, "from": 1000000, "to": 1022000}],
				             "finished": true, "retiredAt": null}],
				 "weather": [{"t": -60000, "airTemperature": 30.0, "trackTemperature": 38.3, "humidity": 68.0,
				              "rainfall": false, "windSpeed": 0.8}]}
				""");
	}

	@Test
	void returnsOneMoment() {
		given(service.frame(5, START.plusSeconds(60))).willReturn(PlaybackFrame.at(START.plusSeconds(60), 57)
				.car(new PlaybackFrame.CarState(1, 0.62, START.plusSeconds(60), Status.RUNNING, -1200.5, 300.25))
				.build());

		assertThat(mvc.get().uri("/api/sessions/5/replay/frame?at=2024-03-02T15:04:42.341Z")).hasStatusOk().bodyJson()
				.isLenientlyEqualTo("""
						{"at": "2024-03-02T15:04:42.341Z", "leaderLap": 1,
						 "cars": [{"number": 1, "position": 1, "lap": 1, "progress": 0.62, "status": "RUNNING",
						           "x": -1200.5, "y": 300.25}]}
						""");
	}

	@Test
	void aSessionWithoutTimingIs404() {
		given(service.replayResponse(9)).willThrow(new NotFoundException("Timing for session", 9));

		assertThat(mvc.get().uri("/api/sessions/9/replay")).hasStatus(HttpStatus.NOT_FOUND);
	}

}

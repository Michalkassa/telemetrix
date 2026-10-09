package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.ingestion.RaceImportCoordinator.ImportStatus;
import com.telemetrix.ingestion.RaceImportCoordinator.State;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(RaceImportController.class)
class RaceImportControllerTest {

	static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	RaceImportCoordinator coordinator;

	@Test
	void showsTheImportStatus() {
		given(coordinator.status(7)).willReturn(new ImportStatus(7, State.FAILED, NOW, "OpenF1 GET /laps failed"));

		assertThat(mvc.get().uri("/api/races/7/import-status")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"raceId": 7, "state": "FAILED", "since": "2026-10-09T12:00:00Z", "message": "OpenF1 GET /laps failed"}
				""");
	}

	@Test
	void openingARaceThatNeedsDownloadingIs202() {
		given(coordinator.open(7)).willReturn(new ImportStatus(7, State.IMPORTING, NOW, null));

		assertThat(mvc.post().uri("/api/races/7/import")).hasStatus(HttpStatus.ACCEPTED)
				.bodyJson().extractingPath("$.state").isEqualTo("IMPORTING");
	}

	@Test
	void openingARaceThatIsReadyIs200() {
		given(coordinator.open(7)).willReturn(new ImportStatus(7, State.READY, NOW, null));

		assertThat(mvc.post().uri("/api/races/7/import")).hasStatusOk()
				.bodyJson().extractingPath("$.state").isEqualTo("READY");
	}

	@Test
	void anUnknownRaceIs404() {
		given(coordinator.status(99)).willThrow(new NotFoundException("Race", 99));

		assertThat(mvc.get().uri("/api/races/99/import-status")).hasStatus(HttpStatus.NOT_FOUND);
	}

}

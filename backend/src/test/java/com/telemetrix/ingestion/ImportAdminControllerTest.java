package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.common.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ImportAdminController.class)
class ImportAdminControllerTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	CalendarImporter calendarImporter;

	@MockitoBean
	RaceImporter raceImporter;

	@Test
	void importsASeasonAndReturnsTheReport() {
		given(calendarImporter.importSeason(2024)).willReturn(new ImportReport(
				Map.of("races", new ImportReport.Counts(24, 0, 0, 0)), List.of("meeting 1228 skipped")));

		assertThat(mvc.post().uri("/api/admin/import/seasons/2024"))
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"counts": {"races": {"inserted": 24, "updated": 0, "unchanged": 0, "skipped": 0}},
						 "notes": ["meeting 1228 skipped"]}
						""");
	}

	@Test
	void rejectsASeasonBeforeTheChampionship() {
		assertThat(mvc.post().uri("/api/admin/import/seasons/1900")).hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void importsARace() {
		given(raceImporter.importRace(42)).willReturn(new ImportReport(
				Map.of("sessions", new ImportReport.Counts(5, 0, 0, 0)), List.of()));

		assertThat(mvc.post().uri("/api/admin/import/races/42"))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.counts.sessions.inserted").isEqualTo(5);
	}

	@Test
	void anUnknownRaceIs404() {
		given(raceImporter.importRace(42)).willThrow(new NotFoundException("Race", 42));

		assertThat(mvc.post().uri("/api/admin/import/races/42")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void anUnavailableSourceIs502() {
		given(calendarImporter.importSeason(2024)).willThrow(new ExternalApiException(
				"Jolpica GET /2024/races.json failed after 4 attempts: 503", null));

		assertThat(mvc.post().uri("/api/admin/import/seasons/2024"))
				.hasStatus(HttpStatus.BAD_GATEWAY)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.extractingPath("$.detail").asString().contains("Jolpica");
	}

}

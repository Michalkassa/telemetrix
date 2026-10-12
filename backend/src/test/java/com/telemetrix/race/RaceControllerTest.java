package com.telemetrix.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.common.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// The contract in docs/api.md, as tests.
@WebMvcTest(RaceController.class)
// controller behaviour only: who may call it is AuthorizationMatrixTest's, against the real filter chain
@AutoConfigureMockMvc(addFilters = false)
class RaceControllerTest {

	static final RaceDtos.CircuitResponse BAHRAIN = new RaceDtos.CircuitResponse("bahrain",
			"Bahrain International Circuit", "Sakhir", "Bahrain");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	RaceQueryService service;

	@Test
	void listsSeasons() {
		given(service.seasons()).willReturn(List.of(new RaceDtos.SeasonResponse(2024, 24),
				new RaceDtos.SeasonResponse(2023, 22)));

		assertThat(mvc.get().uri("/api/seasons")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				[{"year": 2024, "raceCount": 24}, {"year": 2023, "raceCount": 22}]
				""");
	}

	@Test
	void listsASeasonsRaces() {
		given(service.racesOf(2024)).willReturn(new RaceDtos.SeasonRacesResponse(2024, List.of(
				new RaceDtos.RaceSummary(1L, 1, "Bahrain Grand Prix", LocalDate.parse("2024-03-02"), BAHRAIN, true))));

		assertThat(mvc.get().uri("/api/seasons/2024/races")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"year": 2024, "races": [
				  {"id": 1, "round": 1, "name": "Bahrain Grand Prix", "date": "2024-03-02",
				   "circuit": {"ref": "bahrain", "name": "Bahrain International Circuit", "locality": "Sakhir",
				               "country": "Bahrain"},
				   "hasOpenF1Data": true}]}
				""");
	}

	@Test
	void anUnknownSeasonIs404() {
		given(service.racesOf(1999)).willThrow(new NotFoundException("Season", 1999));

		assertThat(mvc.get().uri("/api/seasons/1999/races")).hasStatus(HttpStatus.NOT_FOUND)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson().extractingPath("$.detail").isEqualTo("Season 1999 not found");
	}

	@Test
	void aYearOutsideTheChampionshipIs400() {
		assertThat(mvc.get().uri("/api/seasons/1900/races")).hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void showsARace() {
		given(service.race(1L)).willReturn(new RaceDtos.RaceDetail(1L, 2024, 1, "Bahrain Grand Prix",
				LocalDate.parse("2024-03-02"), BAHRAIN, 1229, Instant.parse("2026-10-09T14:35:47Z"),
				List.of(new RaceDtos.SessionResponse(5L, "Race", Instant.parse("2024-03-02T15:00:00Z"),
						Instant.parse("2024-03-02T17:00:00Z")))));

		assertThat(mvc.get().uri("/api/races/1")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"id": 1, "season": 2024, "round": 1, "name": "Bahrain Grand Prix", "date": "2024-03-02",
				 "circuit": {"ref": "bahrain", "name": "Bahrain International Circuit", "locality": "Sakhir",
				             "country": "Bahrain"},
				 "openf1MeetingKey": 1229, "importedAt": "2026-10-09T14:35:47Z",
				 "sessions": [{"id": 5, "name": "Race", "startsAt": "2024-03-02T15:00:00Z",
				               "endsAt": "2024-03-02T17:00:00Z"}]}
				""");
	}

	@Test
	void anUnknownRaceIs404() {
		given(service.race(42L)).willThrow(new NotFoundException("Race", 42L));

		assertThat(mvc.get().uri("/api/races/42")).hasStatus(HttpStatus.NOT_FOUND)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

}

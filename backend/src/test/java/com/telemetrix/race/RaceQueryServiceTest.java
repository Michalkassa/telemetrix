package com.telemetrix.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.telemetrix.circuit.Circuit;
import com.telemetrix.common.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// The read side of races: entities in, response records out. Repositories are mocks.
class RaceQueryServiceTest {

	SeasonRepository seasons = mock(SeasonRepository.class);

	RaceRepository races = mock(RaceRepository.class);

	RaceQueryService service = new RaceQueryService(seasons, races);

	Season season2024 = new Season((short) 2024);

	Circuit bahrain = bahrain();

	@Test
	void listsSeasonsWithRaceCounts() {
		given(seasons.findAllWithRaceCounts()).willReturn(List.of(new SeasonRepository.SeasonRaceCount((short) 2024, 24L),
				new SeasonRepository.SeasonRaceCount((short) 2023, 22L)));

		assertThat(service.seasons()).containsExactly(new RaceDtos.SeasonResponse(2024, 24),
				new RaceDtos.SeasonResponse(2023, 22));
	}

	@Test
	void listsASeasonsRacesWithCircuitAndOpenF1Availability() {
		var linked = new Race(season2024, (short) 1, bahrain, "Bahrain Grand Prix", LocalDate.parse("2024-03-02"));
		linked.setOpenf1MeetingKey(1229);
		var unlinked = new Race(season2024, (short) 2, bahrain, "Second Race", LocalDate.parse("2024-03-09"));
		given(seasons.existsById((short) 2024)).willReturn(true);
		given(races.findAllBySeasonYearOrderByRound((short) 2024)).willReturn(List.of(linked, unlinked));

		var response = service.racesOf(2024);

		assertThat(response.year()).isEqualTo(2024);
		assertThat(response.races()).extracting(RaceDtos.RaceSummary::round, RaceDtos.RaceSummary::name,
				RaceDtos.RaceSummary::hasOpenF1Data)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(1, "Bahrain Grand Prix", true),
						org.assertj.core.groups.Tuple.tuple(2, "Second Race", false));
		assertThat(response.races().getFirst().circuit())
				.isEqualTo(new RaceDtos.CircuitResponse("bahrain", "Bahrain International Circuit", "Sakhir", "Bahrain"));
	}

	@Test
	void anUnknownSeasonIsNotFound() {
		given(seasons.existsById((short) 1999)).willReturn(false);

		assertThatThrownBy(() -> service.racesOf(1999)).isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Season 1999");
	}

	@Test
	void showsARaceWithItsSessions() {
		var race = new Race(season2024, (short) 1, bahrain, "Bahrain Grand Prix", LocalDate.parse("2024-03-02"));
		race.setOpenf1MeetingKey(1229);
		var session = new Session("Race", Instant.parse("2024-03-02T15:00:00Z"));
		session.setEndsAt(Instant.parse("2024-03-02T17:00:00Z"));
		race.addSession(session);
		given(races.findWithDetailsById(1L)).willReturn(Optional.of(race));

		var detail = service.race(1L);

		assertThat(detail.season()).isEqualTo(2024);
		assertThat(detail.round()).isEqualTo(1);
		assertThat(detail.openf1MeetingKey()).isEqualTo(1229);
		assertThat(detail.importedAt()).isNull();
		assertThat(detail.sessions()).extracting(RaceDtos.SessionResponse::name, RaceDtos.SessionResponse::startsAt)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("Race", Instant.parse("2024-03-02T15:00:00Z")));
	}

	@Test
	void anUnknownRaceIsNotFound() {
		given(races.findWithDetailsById(42L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.race(42L)).isInstanceOf(NotFoundException.class).hasMessageContaining("Race 42");
	}

	private static Circuit bahrain() {
		var circuit = new Circuit("bahrain", "Bahrain International Circuit");
		circuit.update("Bahrain International Circuit", "Sakhir", "Bahrain", new BigDecimal("26.0325"),
				new BigDecimal("50.5106"));
		return circuit;
	}

}

package com.telemetrix.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.telemetrix.Fixtures;
import com.telemetrix.RepositoryTest;
import com.telemetrix.circuit.Circuit;
import com.telemetrix.circuit.CircuitRepository;
import com.telemetrix.common.NotFoundException;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.race.Race;
import com.telemetrix.race.RaceRepository;
import com.telemetrix.race.Season;
import com.telemetrix.race.SeasonRepository;
import com.telemetrix.race.Session;
import com.telemetrix.timing.Lap;
import com.telemetrix.timing.LapRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Telemetry is downloaded the first time someone asks for a car in a session, then served from the
// database. OpenF1 is a mock fed with a recorded excerpt of Verstappen's 2024 Bahrain GP.
@RepositoryTest
@Import({ TelemetryService.class, TelemetryStore.class, TelemetryServiceTest.FixedClock.class })
class TelemetryServiceTest {

	@TestConfiguration
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);
		}

	}

	@Autowired
	TelemetryService service;

	@Autowired
	SeasonRepository seasons;

	@Autowired
	CircuitRepository circuits;

	@Autowired
	RaceRepository races;

	@Autowired
	LapRepository laps;

	@Autowired
	TestEntityManager em;

	@MockitoBean
	OpenF1Client openF1;

	long sessionId;

	@BeforeEach
	void bahrain2024RaceWithVerstappensLaps10And11() {
		var race = new Race(seasons.save(new Season((short) 2024)), (short) 1,
				circuits.save(new Circuit("bahrain", "Bahrain International Circuit")), "Bahrain Grand Prix",
				LocalDate.parse("2024-03-02"));
		var session = new Session("Race", Instant.parse("2024-03-02T15:00:00Z"));
		session.setEndsAt(Instant.parse("2024-03-02T17:00:00Z"));
		session.setOpenf1SessionKey(9472);
		race.addSession(session);
		races.save(race);
		em.flush();
		sessionId = session.getId();
		var lap10 = new Lap(session, 1, 10, Instant.parse("2024-03-02T15:18:15.404Z"));
		lap10.setTimes(30_986, 42_009, 23_965, 96_960);
		var lap11 = new Lap(session, 1, 11, Instant.parse("2024-03-02T15:19:52.364Z"));
		laps.save(lap10);
		laps.save(lap11);
		given(openF1.carData(9472, 1)).willReturn(Fixtures.openF1CarData(9472, 1));
		given(openF1.locations(9472, 1)).willReturn(Fixtures.openF1Locations(9472, 1));
	}

	@Test
	void theFirstRequestDownloadsTheCarsTelemetryAndReturnsOneLap() {
		var lap10 = service.telemetry(sessionId, 1, 10);

		assertThat(lap10.points()).hasSize(366);
		assertThat(lap10.points().getFirst().t()).isEqualTo(Instant.parse("2024-03-02T15:18:16.271Z"));
		assertThat(lap10.points().getLast().t()).isEqualTo(Instant.parse("2024-03-02T15:19:52.312Z"));
		assertThat(lap10.points()).extracting(TelemetryResponse.Point::speed).contains(298).allMatch(s -> s <= 298);
		assertThat(lap10.points()).allSatisfy(p -> assertThat(p.x()).isNotNull());
		assertThat(lap10.from()).isEqualTo(Instant.parse("2024-03-02T15:18:15.404Z"));
		assertThat(lap10.to()).isEqualTo(Instant.parse("2024-03-02T15:19:52.364Z"));
		assertThat(lap10.gaps()).isEmpty();
	}

	@Test
	void laterRequestsAreServedFromTheDatabaseWithoutCallingOpenF1() {
		service.telemetry(sessionId, 1, 10);

		var again = service.telemetry(sessionId, 1, 10);
		var whole = service.telemetry(sessionId, 1, null);

		assertThat(again.points()).hasSize(366);
		assertThat(whole.points()).hasSize(806);
		verify(openF1, times(1)).carData(9472, 1);
		verify(openF1, times(1)).locations(9472, 1);
	}

	@Test
	void theWholeSessionIncludesItsRecordedGaps() {
		var whole = service.telemetry(sessionId, 1, null);

		assertThat(whole.gaps()).singleElement().satisfies(gap -> {
			assertThat(gap.from()).isEqualTo(Instant.parse("2024-03-02T14:03:49.138Z"));
			assertThat(gap.to()).isEqualTo(Instant.parse("2024-03-02T15:18:10.031Z"));
		});
	}

	@Test
	void aLapThatIsNotThereIsNotFound() {
		assertThatThrownBy(() -> service.telemetry(sessionId, 1, 30)).isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Lap 30");
	}

	@Test
	void anUnknownSessionIsNotFound() {
		assertThatThrownBy(() -> service.telemetry(999_999, 1, null)).isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Session 999999");
	}

}

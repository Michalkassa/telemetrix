package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.telemetrix.Fixtures;
import com.telemetrix.RepositoryTest;
import com.telemetrix.circuit.Circuit;
import com.telemetrix.circuit.CircuitRepository;
import com.telemetrix.common.NotFoundException;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.ingestion.openf1.OpenF1Session;
import com.telemetrix.race.Race;
import com.telemetrix.race.RaceRepository;
import com.telemetrix.race.Season;
import com.telemetrix.race.SeasonRepository;
import com.telemetrix.race.Session;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Downloading one race's data on demand, when a user opens it.
@RepositoryTest
@Import({ RaceImporter.class, CalendarValidator.class, RaceImporterTest.FixedClock.class })
class RaceImporterTest {

	// "now" for these tests: well after the 2024 Bahrain GP, before the 2027 season
	static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

	@TestConfiguration
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

	}

	@Autowired
	RaceImporter importer;

	@Autowired
	RaceRepository races;

	@Autowired
	SeasonRepository seasons;

	@Autowired
	CircuitRepository circuits;

	@Autowired
	TestEntityManager em;

	@MockitoBean
	OpenF1Client openF1;

	Circuit bahrainCircuit;

	@BeforeEach
	void seedCalendar() {
		seasons.save(new Season((short) 2024));
		seasons.save(new Season((short) 2008));
		seasons.save(new Season((short) 2027));
		bahrainCircuit = circuits.save(new Circuit("bahrain", "Bahrain International Circuit"));
	}

	@Test
	void downloadsTheRacesSessionsAndMarksItImported() {
		long id = race(2024, 1, "2024-03-02", 1229).getId();
		given(openF1.sessions(1229)).willReturn(Fixtures.openF1Sessions(1229));

		var report = importer.importRace(id);
		em.flush();
		em.clear();

		var race = races.findById(id).orElseThrow();
		assertThat(race.getSessions()).extracting(Session::getName)
				.containsExactly("Practice 1", "Practice 2", "Practice 3", "Qualifying", "Race");
		var raceSession = race.getSessions().getLast();
		assertThat(raceSession.getOpenf1SessionKey()).isEqualTo(9472);
		assertThat(raceSession.getStartsAt()).isEqualTo(Instant.parse("2024-03-02T15:00:00Z"));
		assertThat(raceSession.getEndsAt()).isEqualTo(Instant.parse("2024-03-02T17:00:00Z"));
		assertThat(race.getImportedAt()).isEqualTo(NOW);
		assertThat(report.count("sessions").inserted()).isEqualTo(5);
	}

	@Test
	void anAlreadyImportedRaceMakesNoApiCalls() {
		long id = race(2024, 1, "2024-03-02", 1229).getId();
		given(openF1.sessions(1229)).willReturn(Fixtures.openF1Sessions(1229));
		importer.importRace(id);

		var second = importer.importRace(id);

		verify(openF1, times(1)).sessions(1229);
		assertThat(second.notes()).anySatisfy(note -> assertThat(note).contains("already imported"));
	}

	@Test
	void aRaceWithoutOpenF1DataImportsNothingAndSaysSo() {
		long id = race(2008, 1, "2008-03-16", null).getId();

		var report = importer.importRace(id);

		verify(openF1, never()).sessions(anyInt());
		assertThat(report.notes()).anySatisfy(note -> assertThat(note).contains("no OpenF1 data"));
		assertThat(races.findById(id).orElseThrow().getImportedAt()).isNull();
	}

	@Test
	void aRaceThatHasNotHappenedYetIsNotMarkedImported() {
		// so its sessions are refreshed the next time someone opens it
		long id = race(2027, 1, "2027-03-14", 1300).getId();
		given(openF1.sessions(1300)).willReturn(List.of(session(9900, "Race", "2027-03-14T15:00:00Z",
				"2027-03-14T17:00:00Z")));

		importer.importRace(id);

		assertThat(races.findById(id).orElseThrow().getImportedAt()).isNull();
	}

	@Test
	void reimportingAnUnfinishedRaceUpdatesMovedSessions() {
		long id = race(2027, 1, "2027-03-14", 1300).getId();
		given(openF1.sessions(1300)).willReturn(List.of(session(9900, "Race", "2027-03-14T15:00:00Z",
				"2027-03-14T17:00:00Z")));
		importer.importRace(id);
		given(openF1.sessions(1300)).willReturn(List.of(session(9900, "Race", "2027-03-14T14:00:00Z",
				"2027-03-14T16:00:00Z")));

		var report = importer.importRace(id);

		assertThat(races.findById(id).orElseThrow().getSessions().getFirst().getStartsAt())
				.isEqualTo(Instant.parse("2027-03-14T14:00:00Z"));
		assertThat(report.count("sessions").updated()).isEqualTo(1);
	}

	@Test
	void invalidAndCancelledSessionsAreSkippedAndCounted() {
		long id = race(2024, 1, "2024-03-02", 1229).getId();
		given(openF1.sessions(1229)).willReturn(List.of(
				session(9472, "Race", "2024-03-02T15:00:00Z", "2024-03-02T17:00:00Z"),
				session(9001, "Practice 1", "2024-02-29T11:30:00Z", "2024-02-29T11:00:00Z"), // ends first
				session(9002, "Sprint", "2024-06-01T11:30:00Z", "2024-06-01T12:30:00Z"), // wrong weekend
				new OpenF1Session(9003, 1229, "Practice 2", "Practice", 63, odt("2024-02-29T15:00:00Z"),
						odt("2024-02-29T16:00:00Z"), 2024, true))); // cancelled

		var report = importer.importRace(id);

		assertThat(races.findById(id).orElseThrow().getSessions()).extracting(Session::getName).containsExactly("Race");
		assertThat(report.count("sessions").skipped()).isEqualTo(3);
		assertThat(report.notes()).anySatisfy(note -> assertThat(note).contains("Practice 1").contains("ends before"))
				.anySatisfy(note -> assertThat(note).contains("Sprint").contains("weekend"))
				.anySatisfy(note -> assertThat(note).contains("Practice 2").contains("cancelled"));
	}

	@Test
	void anUnknownRaceIsNotFound() {
		assertThatThrownBy(() -> importer.importRace(999_999)).isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Race 999999");
	}

	private Race race(int year, int round, String date, Integer meetingKey) {
		var race = new Race(seasons.findById((short) year).orElseThrow(), (short) round, bahrainCircuit,
				"Grand Prix " + year, LocalDate.parse(date));
		race.setOpenf1MeetingKey(meetingKey);
		return races.save(race);
	}

	private static OpenF1Session session(int key, String name, String start, String end) {
		return new OpenF1Session(key, 0, name, name, 63, odt(start), odt(end), 2024, false);
	}

	private static OffsetDateTime odt(String value) {
		return OffsetDateTime.parse(value);
	}

}

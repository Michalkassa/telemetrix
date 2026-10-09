package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.telemetrix.Fixtures;
import com.telemetrix.RepositoryTest;
import com.telemetrix.circuit.CircuitRepository;
import com.telemetrix.ingestion.jolpica.JolpicaClient;
import com.telemetrix.ingestion.jolpica.JolpicaRace;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.race.Race;
import com.telemetrix.race.RaceRepository;
import com.telemetrix.race.SeasonRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Importing a season's calendar into the real (Testcontainers) database. The API clients are
// mocks fed from recorded responses.
@RepositoryTest
@Import({ CalendarImporter.class, CalendarValidator.class })
class CalendarImporterTest {

	@Autowired
	CalendarImporter importer;

	@Autowired
	SeasonRepository seasons;

	@Autowired
	CircuitRepository circuits;

	@Autowired
	RaceRepository races;

	@Autowired
	TestEntityManager em;

	@MockitoBean
	JolpicaClient jolpica;

	@MockitoBean
	OpenF1Client openF1;

	@Test
	void createsTheSeasonItsCircuitsAndItsRacesWithOfficialRounds() {
		given(jolpica.races(2024)).willReturn(Fixtures.jolpicaRaces(2024));
		given(openF1.meetings(2024)).willReturn(Fixtures.openF1Meetings(2024));

		var report = importer.importSeason(2024);

		assertThat(seasons.existsById((short) 2024)).isTrue();
		assertThat(races.findBySeasonYearOrderByRaceDate((short) 2024)).hasSize(24);
		var monaco = races.findBySeasonYearAndRound((short) 2024, (short) 8).orElseThrow();
		assertThat(monaco.getName()).isEqualTo("Monaco Grand Prix");
		assertThat(monaco.getRaceDate()).isEqualTo(LocalDate.parse("2024-05-26"));
		assertThat(monaco.getCircuit().getRef()).isEqualTo("monaco");
		assertThat(circuits.findByRef("bahrain").orElseThrow().getLocality()).isEqualTo("Sakhir");
		assertThat(report.count("races").inserted()).isEqualTo(24);
		assertThat(report.count("circuits").inserted()).isEqualTo(24);
	}

	@Test
	void linksOpenF1MeetingsToRacesByDate() {
		given(jolpica.races(2024)).willReturn(Fixtures.jolpicaRaces(2024));
		given(openF1.meetings(2024)).willReturn(Fixtures.openF1Meetings(2024));

		var report = importer.importSeason(2024);

		var bahrain = races.findBySeasonYearAndRound((short) 2024, (short) 1).orElseThrow();
		assertThat(bahrain.getOpenf1MeetingKey()).isEqualTo(1229);
		assertThat(bahrain.getCircuit().getOpenf1CircuitKey()).isEqualTo(63);
		assertThat(races.findBySeasonYearOrderByRaceDate((short) 2024))
				.allSatisfy(race -> assertThat(race.getOpenf1MeetingKey()).isNotNull());
		assertThat(report.count("meetings").updated()).isEqualTo(24);
	}

	@Test
	void skipsPreSeasonTestingAndCancelledMeetings() {
		given(jolpica.races(2023)).willReturn(Fixtures.jolpicaRaces(2023));
		given(openF1.meetings(2023)).willReturn(Fixtures.openF1Meetings(2023));

		var report = importer.importSeason(2023);

		assertThat(races.findBySeasonYearOrderByRaceDate((short) 2023)).hasSize(22)
				.allSatisfy(race -> assertThat(race.getOpenf1MeetingKey()).isNotNull());
		assertThat(report.count("meetings").skipped()).isEqualTo(2);
		assertThat(report.notes()).anySatisfy(note -> assertThat(note).contains("Pre-Season Testing"))
				.anySatisfy(note -> assertThat(note).contains("Emilia Romagna Grand Prix").contains("cancelled"));
	}

	@Test
	void importingTheSameSeasonTwiceChangesNothing() {
		given(jolpica.races(2024)).willReturn(Fixtures.jolpicaRaces(2024));
		given(openF1.meetings(2024)).willReturn(Fixtures.openF1Meetings(2024));
		importer.importSeason(2024);
		em.flush();
		em.clear();
		long raceRows = races.count();
		long circuitRows = circuits.count();

		var second = importer.importSeason(2024);

		assertThat(races.count()).isEqualTo(raceRows);
		assertThat(circuits.count()).isEqualTo(circuitRows);
		assertThat(second.count("races").inserted()).isZero();
		assertThat(second.count("races").updated()).isZero();
		assertThat(second.count("races").unchanged()).isEqualTo(24);
		assertThat(second.count("meetings").updated()).isZero();
	}

	@Test
	void aChangedRaceInTheSourceUpdatesTheRow() {
		given(openF1.meetings(2024)).willReturn(Fixtures.openF1Meetings(2024));
		given(jolpica.races(2024)).willReturn(Fixtures.jolpicaRaces(2024));
		importer.importSeason(2024);
		var renamed = new ArrayList<>(Fixtures.jolpicaRaces(2024));
		var r1 = renamed.getFirst();
		renamed.set(0, new JolpicaRace(r1.season(), r1.round(), "Gulf Air Bahrain Grand Prix", r1.circuit(), r1.date()));
		given(jolpica.races(2024)).willReturn(renamed);

		var report = importer.importSeason(2024);

		assertThat(races.findBySeasonYearAndRound((short) 2024, (short) 1)).map(Race::getName)
				.hasValue("Gulf Air Bahrain Grand Prix");
		assertThat(report.count("races").updated()).isEqualTo(1);
		assertThat(report.count("races").unchanged()).isEqualTo(23);
	}

	@Test
	void anInvalidRaceIsSkippedAndCounted() {
		var withBroken = new ArrayList<>(Fixtures.jolpicaRaces(2024));
		var r1 = withBroken.getFirst();
		withBroken.set(0, new JolpicaRace(r1.season(), r1.round(), r1.raceName(), r1.circuit(), null));
		given(jolpica.races(2024)).willReturn(withBroken);
		given(openF1.meetings(2024)).willReturn(Fixtures.openF1Meetings(2024));

		var report = importer.importSeason(2024);

		assertThat(races.findBySeasonYearOrderByRaceDate((short) 2024)).hasSize(23);
		assertThat(report.count("races").skipped()).isEqualTo(1);
		assertThat(report.notes()).anySatisfy(note -> assertThat(note).contains("Bahrain Grand Prix").contains("date"));
	}

	@Test
	void seasonsBeforeOpenF1NeverCallIt() {
		given(jolpica.races(2008)).willReturn(java.util.List.of());

		importer.importSeason(2008);

		verify(openF1, never()).meetings(anyInt());
	}

}

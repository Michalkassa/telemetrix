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
import com.telemetrix.circuit.CircuitLayout;
import com.telemetrix.circuit.CircuitLayoutStore;
import com.telemetrix.circuit.CircuitRepository;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.common.NotFoundException;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.ingestion.openf1.OpenF1Lap;
import com.telemetrix.ingestion.openf1.OpenF1Session;
import com.telemetrix.timing.Lap;
import com.telemetrix.timing.LapRepository;
import com.telemetrix.timing.SessionPitStopRepository;
import com.telemetrix.timing.TireStintRepository;
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
import java.util.Optional;
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
@Import({ RaceImporter.class, CalendarValidator.class, SessionTimingImporter.class, LapConsistencyValidator.class,
		CircuitLayoutImporter.class, CircuitLayoutStore.class, RaceImporterTest.FixedClock.class })
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

	@MockitoBean
	MultiviewerClient multiviewer;

	@Autowired
	CircuitLayoutStore layouts;

	@Autowired
	LapRepository laps;

	@Autowired
	TireStintRepository stints;

	@Autowired
	SessionPitStopRepository pitStops;

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
	void downloadsTheRacesLapsStintsAndPitStops() {
		long id = race(2024, 1, "2024-03-02", 1229).getId();
		givenBahrain2024();

		var report = importer.importRace(id);
		em.flush();
		em.clear();

		var raceSession = races.findById(id).orElseThrow().getSessions().getLast();
		var raceLaps = laps.findBySessionOrderByDriverNumberAscLapNumberAsc(raceSession);
		assertThat(raceLaps).hasSize(1129);
		assertThat(raceLaps).filteredOn(l -> l.getDriverNumber() == 1).hasSize(57); // the winner
		assertThat(raceLaps).filteredOn(l -> l.getDriverNumber() == 2).hasSize(55); // two laps down
		assertThat(raceLaps).filteredOn(Lap::isComplete).allSatisfy(lap -> assertThat(lap.timingPoints()).hasSize(4));
		assertThat(raceLaps).filteredOn(l -> !l.isComplete()).hasSize(2);
		assertThat(stints.countBySession(raceSession)).isEqualTo(63);
		assertThat(pitStops.countBySession(raceSession)).isEqualTo(43);
		assertThat(report.count("laps").inserted()).isEqualTo(1129);
		assertThat(report.notes()).noneSatisfy(note -> assertThat(note).startsWithIgnoringCase("error"));
	}

	@Test
	void downloadsTheCircuitsTrackMapWithSectorEndsFromTheRacesMiniSectors() {
		bahrainCircuit.linkOpenF1Circuit(63);
		long id = race(2024, 1, "2024-03-02", 1229).getId();
		givenBahrain2024();
		given(multiviewer.circuit(63, 2024)).willReturn(Optional.of(Fixtures.multiviewerCircuit(63, 2024)));

		var report = importer.importRace(id);

		var layout = layouts.find(bahrainCircuit.getId(), 2024).orElseThrow();
		assertThat(layout.sectorEndsSource()).isEqualTo(CircuitLayout.SectorEndsSource.TRACK); // 10 + 11 + 7 mini-sectors
		assertThat(report.count("layouts").inserted()).isEqualTo(1);
	}

	@Test
	void onlyRaceAndSprintSessionsGetTimingData() {
		long id = race(2024, 1, "2024-03-02", 1229).getId();
		givenBahrain2024();

		importer.importRace(id);

		verify(openF1).laps(9472);
		verify(openF1, never()).laps(9465); // Practice 1
		verify(openF1, never()).laps(9468); // Qualifying
	}

	@Test
	void reimportingAnUnfinishedRaceReplacesItsTimingData() {
		long id = race(2027, 1, "2027-03-14", 1300).getId();
		given(openF1.sessions(1300)).willReturn(List.of(session(9900, "Race", "2027-03-14T15:00:00Z",
				"2027-03-14T17:00:00Z")));
		given(openF1.laps(9900)).willReturn(List.of(openF1Lap(1, 1), openF1Lap(1, 2)));
		importer.importRace(id);
		given(openF1.laps(9900)).willReturn(List.of(openF1Lap(1, 1), openF1Lap(1, 2), openF1Lap(1, 3)));

		importer.importRace(id);
		em.flush();

		var raceSession = races.findById(id).orElseThrow().getSessions().getFirst();
		assertThat(laps.findBySessionOrderByDriverNumberAscLapNumberAsc(raceSession)).extracting(Lap::getLapNumber)
				.containsExactly(1, 2, 3);
	}

	@Test
	void lapProblemsAreReported() {
		long id = race(2027, 1, "2027-03-14", 1300).getId();
		given(openF1.sessions(1300)).willReturn(List.of(session(9900, "Race", "2027-03-14T15:00:00Z",
				"2027-03-14T17:00:00Z")));
		given(openF1.laps(9900)).willReturn(List.of(openF1Lap(1, 1), openF1Lap(1, 3)));

		var report = importer.importRace(id);

		assertThat(report.notes()).anySatisfy(note -> assertThat(note).startsWith("ERROR").contains("car 1"));
		assertThat(report.count("laps").inserted()).isEqualTo(2); // kept, but reported
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

	private void givenBahrain2024() {
		given(openF1.sessions(1229)).willReturn(Fixtures.openF1Sessions(1229));
		given(openF1.laps(9472)).willReturn(Fixtures.openF1Laps(9472));
		given(openF1.stints(9472)).willReturn(Fixtures.openF1Stints(9472));
		given(openF1.pitStops(9472)).willReturn(Fixtures.openF1PitStops(9472));
	}

	private static OpenF1Lap openF1Lap(int car, int number) {
		return new OpenF1Lap(car, number, odt("2027-03-14T15:00:00Z").plusSeconds(90L * number), 30.0, 35.0, 25.0,
				90.0, null, null, null, false, null, null, null);
	}

	private static OffsetDateTime odt(String value) {
		return OffsetDateTime.parse(value);
	}

}

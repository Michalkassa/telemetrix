package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.race.Race;
import com.telemetrix.race.RaceRepository;
import com.telemetrix.race.Season;
import com.telemetrix.race.SeasonRepository;
import com.telemetrix.race.Session;
import com.telemetrix.timing.Lap;
import com.telemetrix.timing.LapRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// The track map of a race's circuit, fetched once per circuit and year (docs/adr/0003).
@RepositoryTest
@Import({ CircuitLayoutImporter.class, CircuitLayoutStore.class })
class CircuitLayoutImporterTest {

	static final SectorSegments BAHRAIN_SEGMENTS = new SectorSegments(10, 11, 7);

	@Autowired
	CircuitLayoutImporter importer;

	@Autowired
	CircuitLayoutStore layouts;

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
	MultiviewerClient multiviewer;

	@MockitoBean
	OpenF1Client openF1;

	Circuit bahrain;

	Race race;

	Session raceSession;

	@BeforeEach
	void bahrain2024() {
		bahrain = new Circuit("bahrain", "Bahrain International Circuit");
		bahrain.linkOpenF1Circuit(63);
		circuits.save(bahrain);
		race = new Race(seasons.save(new Season((short) 2024)), (short) 1, bahrain, "Bahrain Grand Prix",
				LocalDate.parse("2024-03-02"));
		raceSession = new Session("Race", Instant.parse("2024-03-02T15:00:00Z"));
		raceSession.setOpenf1SessionKey(9472);
		race.addSession(raceSession);
		races.save(race);
		em.flush();
	}

	@Test
	void storesTheMultiviewerMapWithSectorEndsFromItsMiniSectors() {
		given(multiviewer.circuit(63, 2024)).willReturn(Optional.of(Fixtures.multiviewerCircuit(63, 2024)));
		var report = ImportReport.builder();

		importer.ensureLayout(race, raceSession, BAHRAIN_SEGMENTS, report);

		CircuitLayout layout = layouts.find(bahrain.getId(), 2024).orElseThrow();
		assertThat(layout.source()).isEqualTo(CircuitLayout.Source.MULTIVIEWER);
		assertThat(layout.rotation()).isEqualTo(92.0);
		assertThat(layout.points()).hasSize(730);
		assertThat(layout.sectorEnds()[0]).isCloseTo(0.3279, within(0.0005));
		assertThat(layout.sectorEnds()[1]).isCloseTo(0.7362, within(0.0005));
		assertThat(layout.sectorEndsSource()).isEqualTo(CircuitLayout.SectorEndsSource.TRACK);
		assertThat(layout.corners()).hasSize(15);
		assertThat(layout.corners().getFirst().progress()).isCloseTo(0.132, within(0.001));
		assertThat(layout.corners().getLast().progress()).isCloseTo(0.922, within(0.001));
		assertThat(report.build().count("layouts").inserted()).isEqualTo(1);
	}

	@Test
	void withoutMiniSectorCountsTheSectorEndsAreEstimatedFromSectorTimes() {
		given(multiviewer.circuit(63, 2024)).willReturn(Optional.of(Fixtures.multiviewerCircuit(63, 2024)));
		saveLap(1, 10, "2024-03-02T15:18:15.404Z", 30_000, 40_000, 25_000, 95_000);
		saveLap(1, 11, "2024-03-02T15:19:52.364Z", 31_000, 40_000, 25_000, 96_000);
		saveLap(1, 12, "2024-03-02T15:21:28.364Z", 29_000, 41_000, 25_000, 95_000);

		importer.ensureLayout(race, raceSession, null, ImportReport.builder());

		var layout = layouts.find(bahrain.getId(), 2024).orElseThrow();
		assertThat(layout.sectorEndsSource()).isEqualTo(CircuitLayout.SectorEndsSource.ESTIMATED);
		assertThat(layout.sectorEnds()[0]).isCloseTo(30.0 / 95, within(1e-6));
		assertThat(layout.sectorEnds()[1]).isCloseTo(70.0 / 95, within(1e-6));
	}

	@Test
	void aStoredLayoutIsNotFetchedAgain() {
		given(multiviewer.circuit(63, 2024)).willReturn(Optional.of(Fixtures.multiviewerCircuit(63, 2024)));
		importer.ensureLayout(race, raceSession, BAHRAIN_SEGMENTS, ImportReport.builder());

		var report = ImportReport.builder();
		importer.ensureLayout(race, raceSession, BAHRAIN_SEGMENTS, report);

		verify(multiviewer, times(1)).circuit(63, 2024);
		assertThat(report.build().count("layouts").unchanged()).isEqualTo(1);
	}

	@Test
	void withoutAMultiviewerMapTheOutlineComesFromTheFastestLapsPositions() {
		given(multiviewer.circuit(63, 2024)).willReturn(Optional.empty());
		saveLap(1, 10, "2024-03-02T15:18:15.404Z", 30_986, 42_009, 23_965, 96_960); // the fastest
		saveLap(16, 10, "2024-03-02T15:18:17.000Z", 31_500, 42_500, 24_500, 98_500);
		given(openF1.locations(9472, 1)).willReturn(Fixtures.openF1Locations(9472, 1));

		importer.ensureLayout(race, raceSession, BAHRAIN_SEGMENTS, ImportReport.builder());

		var layout = layouts.find(bahrain.getId(), 2024).orElseThrow();
		assertThat(layout.source()).isEqualTo(CircuitLayout.Source.OPENF1_LAP);
		assertThat(layout.points()).hasSize(374); // positions during that lap, none of them (0, 0, 0)
		assertThat(layout.points().getFirst()).isEqualTo(new com.telemetrix.circuit.TrackOutline.Point(-372, 1453));
		assertThat(layout.rotation()).isZero();
		assertThat(layout.sectorEndsSource()).isEqualTo(CircuitLayout.SectorEndsSource.ESTIMATED);
		verify(openF1, never()).locations(9472, 16);
	}

	@Test
	void withNoMapAndNoLapsThereIsNoLayoutAndTheReportSaysSo() {
		given(multiviewer.circuit(63, 2024)).willReturn(Optional.empty());
		var report = ImportReport.builder();

		importer.ensureLayout(race, raceSession, null, report);

		assertThat(layouts.find(bahrain.getId(), 2024)).isEmpty();
		assertThat(report.build().notes()).anySatisfy(note -> assertThat(note).contains("no track map"));
		verify(openF1, never()).locations(anyInt(), anyInt());
	}

	private void saveLap(int car, int number, String start, int s1, int s2, int s3, int lapMs) {
		var lap = new Lap(raceSession, car, number, Instant.parse(start));
		lap.setTimes(s1, s2, s3, lapMs);
		laps.save(lap);
	}

}

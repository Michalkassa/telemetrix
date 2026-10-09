package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.Fixtures;
import com.telemetrix.RepositoryTest;
import com.telemetrix.circuit.Circuit;
import com.telemetrix.circuit.CircuitRepository;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.race.Race;
import com.telemetrix.race.RaceRepository;
import com.telemetrix.race.Season;
import com.telemetrix.race.SeasonRepository;
import com.telemetrix.race.Session;
import com.telemetrix.racecontrol.PenaltyKind;
import com.telemetrix.racecontrol.TrackStatusSource;
import com.telemetrix.racecontrol.RaceControlMessageRepository;
import com.telemetrix.racecontrol.SessionPenaltyRepository;
import com.telemetrix.racecontrol.SessionTrackStatusRepository;
import com.telemetrix.racecontrol.TeamRadioClipRepository;
import com.telemetrix.racecontrol.TrackStatus;
import com.telemetrix.racecontrol.WeatherReadingRepository;
import com.telemetrix.timing.Lap;
import com.telemetrix.timing.LapRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// A session's weather, race control (with penalties and track status) and team radio, from recorded
// OpenF1 responses for Singapore 2023's race (session 9165). The F1 archive is off here: track status
// comes from race control (TrackStatusSourceFactoryTest covers the archive).
@RepositoryTest
@Import({ SessionEventsImporter.class, SessionEventsImporterTest.Sources.class })
class SessionEventsImporterTest {

	@TestConfiguration
	static class Sources {

		@Bean
		TrackStatusSourceFactory trackStatusSourceFactory(F1ArchiveClient archive) {
			return new TrackStatusSourceFactory(archive, false);
		}

	}

	@MockitoBean
	OpenF1Client openF1;

	@MockitoBean
	F1ArchiveClient archive;

	@Autowired
	SessionEventsImporter importer;

	@Autowired
	SeasonRepository seasons;

	@Autowired
	CircuitRepository circuits;

	@Autowired
	RaceRepository races;

	@Autowired
	LapRepository laps;

	@Autowired
	WeatherReadingRepository weather;

	@Autowired
	RaceControlMessageRepository events;

	@Autowired
	SessionPenaltyRepository penalties;

	@Autowired
	SessionTrackStatusRepository trackStatus;

	@Autowired
	TeamRadioClipRepository radio;

	@Autowired
	TestEntityManager em;

	Session session;

	@BeforeEach
	void singapore2023() {
		var season = seasons.save(new Season((short) 2023));
		var circuit = circuits.save(new Circuit("marina_bay", "Marina Bay Street Circuit"));
		var race = new Race(season, (short) 16, circuit, "Singapore Grand Prix", LocalDate.parse("2023-09-17"));
		race.setOpenf1MeetingKey(1219);
		session = new Session("Race", Instant.parse("2023-09-17T12:00:00Z"));
		session.setOpenf1SessionKey(9165);
		race.addSession(session);
		races.saveAndFlush(race);
		given(openF1.raceControl(9165)).willReturn(Fixtures.openF1RaceControl(9165));
		given(openF1.weather(9165)).willReturn(Fixtures.openF1Weather(9165));
		given(openF1.teamRadio(9165)).willReturn(Fixtures.openF1TeamRadio(9165));
		given(archive.trackStatus(2023, 9165)).willReturn(Optional.empty());
	}

	@Test
	void storesTheWeatherReadings() {
		var report = importSession();

		var readings = weather.findBySessionOrderByRecordedAtAsc(session);
		assertThat(readings).hasSize(176);
		assertThat(readings.getFirst().getRecordedAt()).isEqualTo(Instant.parse("2023-09-17T11:01:24.028Z"));
		assertThat(readings.getFirst().getAirTemperature()).isEqualTo(30.0);
		assertThat(readings.getFirst().getTrackTemperature()).isEqualTo(38.3);
		assertThat(readings.getFirst().isRainfall()).isFalse();
		assertThat(report.count("weather").inserted()).isEqualTo(176);
	}

	@Test
	void storesEveryRaceControlMessage() {
		importSession();

		var stored = events.findBySessionOrderByOccurredAtAscIdAsc(session);
		assertThat(stored).hasSize(76);
		assertThat(stored.get(18).getMessage()).isEqualTo("SAFETY CAR DEPLOYED");
		assertThat(stored.get(18).getLapNumber()).isEqualTo((short) 20);
		assertThat(stored.get(18).getCategory()).isEqualTo("SafetyCar");
	}

	@Test
	void picksOutThePenalties() {
		var report = importSession();

		var stored = penalties.findBySessionOrderByIssuedAtAsc(session);
		assertThat(stored).singleElement().satisfies(p -> {
			assertThat(p.getDriverNumber()).isEqualTo(14); // Alonso
			assertThat(p.getKind()).isEqualTo(PenaltyKind.TIME);
			assertThat(p.getAmount()).isEqualTo((short) 5);
			assertThat(p.getReason()).startsWith("FAILING TO FOLLOW RACE DIRECTORS INSTRUCTIONS");
			assertThat(p.getIssuedAt()).isEqualTo(Instant.parse("2023-09-17T12:43:50Z"));
			assertThat(p.getLapNumber()).isEqualTo((short) 23);
			assertThat(p.getRaceControlMessage().getMessage()).startsWith("FIA STEWARDS: 5 SECOND TIME PENALTY FOR CAR 14");
		});
		assertThat(report.count("penalties").inserted()).isEqualTo(1);
	}

	@Test
	void storesTheTrackStatusPeriodsWithTheirLaps() {
		givenTheLeadersLapStarts();

		var report = importSession();

		var periods = trackStatus.findBySessionOrderByStartsAtAsc(session);
		assertThat(periods).hasSize(11).allSatisfy(p -> assertThat(p.getSource())
				.isEqualTo(TrackStatusSource.Kind.RACE_CONTROL));
		assertThat(periods).filteredOn(p -> p.getStatus() == TrackStatus.SAFETY_CAR).singleElement()
				.satisfies(sc -> {
					assertThat(sc.getStartsAt()).isEqualTo(Instant.parse("2023-09-17T12:36:41Z"));
					assertThat(sc.getEndsAt()).isEqualTo(Instant.parse("2023-09-17T12:42:50Z"));
					assertThat(sc.getStartLap()).isEqualTo((short) 20);
					assertThat(sc.getEndLap()).isEqualTo((short) 22);
				});
		assertThat(report.notes()).anySatisfy(n -> assertThat(n).contains("track status").contains("RACE_CONTROL"));
	}

	@Test
	void storesTheTeamRadioLinks() {
		importSession();

		var clips = radio.findBySessionOrderByRecordedAtAsc(session);
		assertThat(clips).hasSize(65);
		assertThat(clips.getFirst().getDriverNumber()).isEqualTo(24);
		assertThat(clips.getFirst().getRecordingUrl()).endsWith("GUAZHO01_24_20230917_121303.mp3");
	}

	@Test
	void importingAgainReplacesTheSessionsEvents() {
		importSession();

		importSession();

		assertThat(events.findBySessionOrderByOccurredAtAscIdAsc(session)).hasSize(76);
		assertThat(penalties.findBySessionOrderByIssuedAtAsc(session)).hasSize(1);
		assertThat(weather.findBySessionOrderByRecordedAtAsc(session)).hasSize(176);
		assertThat(trackStatus.findBySessionOrderByStartsAtAsc(session)).hasSize(11);
		assertThat(radio.findBySessionOrderByRecordedAtAsc(session)).hasSize(65);
	}

	private ImportReport importSession() {
		var report = ImportReport.builder();
		importer.importSession(session, 2023, report);
		em.flush();
		em.clear();
		return report.build();
	}

	// the first car to start each lap (OpenF1 laps for 9165, leader only)
	private void givenTheLeadersLapStarts() {
		Fixtures.openF1Laps(9165).stream()
				.filter(l -> l.dateStart() != null)
				.collect(java.util.stream.Collectors.toMap(l -> l.lapNumber(), l -> l, (a, b) ->
						a.dateStart().isBefore(b.dateStart()) ? a : b))
				.values()
				.forEach(l -> laps.save(new Lap(session, l.driverNumber(), l.lapNumber(), l.dateStart().toInstant())));
	}

}

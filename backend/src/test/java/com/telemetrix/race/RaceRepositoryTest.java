package com.telemetrix.race;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.RepositoryTest;
import com.telemetrix.circuit.Circuit;
import com.telemetrix.circuit.CircuitRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@RepositoryTest
class RaceRepositoryTest {

	static final short YEAR = 2024;

	@Autowired
	RaceRepository races;

	@Autowired
	SessionRepository sessions;

	@Autowired
	SeasonRepository seasons;

	@Autowired
	CircuitRepository circuits;

	@Autowired
	TestEntityManager em;

	@Autowired
	EntityManagerFactory emf;

	Season season2024;

	Circuit monza;

	@BeforeEach
	void seed() {
		season2024 = seasons.save(new Season(YEAR));
		monza = circuits.save(new Circuit("monza", "Autodromo Nazionale di Monza"));
	}

	@Test
	void savingARaceKeepsItsSessionsInStartOrder() {
		var race = new Race(season2024, (short) 16, monza, "Italian Grand Prix", LocalDate.parse("2024-09-01"));
		race.addSession(new Session("Race", Instant.parse("2024-09-01T13:00:00Z")));
		race.addSession(new Session("Qualifying", Instant.parse("2024-08-31T14:00:00Z")));
		long id = races.save(race).getId();
		em.flush();
		em.clear(); // forget everything, so the next read really comes from the database

		var loaded = races.findById(id).orElseThrow();

		assertThat(loaded.getName()).isEqualTo("Italian Grand Prix");
		assertThat(loaded.getSeason().getYear()).isEqualTo(YEAR);
		assertThat(loaded.getCircuit().getRef()).isEqualTo("monza");
		assertThat(loaded.getSessions()).extracting(Session::getName).containsExactly("Qualifying", "Race");
		assertThat(loaded.getSessions()).allSatisfy(s -> assertThat(s.getRace()).isSameAs(loaded));
	}

	@Test
	void findsASeasonsRacesOrderedByDate() {
		saveRace(3, "Australian Grand Prix", "2024-03-24", null);
		saveRace(1, "Bahrain Grand Prix", "2024-03-02", null);
		saveRace(2, "Saudi Arabian Grand Prix", "2024-03-09", null);
		seasons.save(new Season((short) 2023));
		races.save(new Race(seasons.findById((short) 2023).orElseThrow(), (short) 1, monza, "Bahrain Grand Prix",
				LocalDate.parse("2023-03-05")));

		var found = races.findBySeasonYearOrderByRaceDate(YEAR);

		assertThat(found).extracting(Race::getName)
				.containsExactly("Bahrain Grand Prix", "Saudi Arabian Grand Prix", "Australian Grand Prix");
	}

	@Test
	void findsARaceByItsOpenF1MeetingKey() {
		saveRace(16, "Italian Grand Prix", "2024-09-01", 1245);

		assertThat(races.findByOpenf1MeetingKey(1245)).map(Race::getName).hasValue("Italian Grand Prix");
		assertThat(races.findByOpenf1MeetingKey(9999)).isEmpty();
	}

	@Test
	void findsARaceBySeasonAndRound() {
		saveRace(16, "Italian Grand Prix", "2024-09-01", null);

		assertThat(races.findBySeasonYearAndRound(YEAR, (short) 16)).map(Race::getName).hasValue("Italian Grand Prix");
		assertThat(races.findBySeasonYearAndRound(YEAR, (short) 17)).isEmpty();
	}

	@Test
	void findsASessionByItsOpenF1SessionKey() {
		var race = new Race(season2024, (short) 16, monza, "Italian Grand Prix", LocalDate.parse("2024-09-01"));
		var raceSession = new Session("Race", Instant.parse("2024-09-01T13:00:00Z"));
		raceSession.setOpenf1SessionKey(9590);
		race.addSession(raceSession);
		races.save(race);

		assertThat(sessions.findByOpenf1SessionKey(9590)).map(Session::getName).hasValue("Race");
		assertThat(sessions.findByOpenf1SessionKey(1)).isEmpty();
	}

	@Test
	void loadingASeasonsRacesWithTheirSessionsRunsOneQuery() {
		for (int round = 1; round <= 3; round++) {
			var race = saveRace(round, "Grand Prix " + round, "2024-03-0" + round, null);
			race.addSession(new Session("Qualifying", Instant.parse("2024-03-0" + round + "T14:00:00Z")));
			race.addSession(new Session("Race", Instant.parse("2024-03-0" + round + "T15:00:00Z")));
		}
		em.flush();
		em.clear();
		Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
		stats.clear();

		var found = races.findBySeasonYearOrderByRaceDate(YEAR);
		int sessionCount = found.stream().mapToInt(r -> r.getSessions().size()).sum();

		assertThat(sessionCount).isEqualTo(6);
		assertThat(stats.getPrepareStatementCount()).as("SQL statements (N+1 check)").isEqualTo(1);
	}

	@Test
	void listsASeasonsRacesByRoundWithTheirCircuitsInOneQuery() {
		var spa = circuits.save(new Circuit("spa", "Circuit de Spa-Francorchamps"));
		races.save(new Race(season2024, (short) 2, spa, "Belgian Grand Prix", LocalDate.parse("2024-07-28")));
		saveRace(1, "Italian Grand Prix", "2024-09-01", null);
		em.flush();
		em.clear();
		Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
		stats.clear();

		var found = races.findAllBySeasonYearOrderByRound(YEAR);

		assertThat(found).extracting(r -> r.getCircuit().getName())
				.containsExactly("Autodromo Nazionale di Monza", "Circuit de Spa-Francorchamps");
		assertThat(stats.getPrepareStatementCount()).as("SQL statements (N+1 check)").isEqualTo(1);
	}

	@Test
	void loadsARaceWithItsSeasonCircuitAndSessionsInOneQuery() {
		var race = saveRace(16, "Italian Grand Prix", "2024-09-01", 1244);
		race.addSession(new Session("Race", Instant.parse("2024-09-01T13:00:00Z")));
		race.addSession(new Session("Qualifying", Instant.parse("2024-08-31T14:00:00Z")));
		em.flush();
		em.clear();
		Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
		stats.clear();

		var found = races.findWithDetailsById(race.getId()).orElseThrow();

		assertThat(found.getSeason().getYear()).isEqualTo(YEAR);
		assertThat(found.getCircuit().getRef()).isEqualTo("monza");
		assertThat(found.getSessions()).extracting(Session::getName).containsExactly("Qualifying", "Race");
		assertThat(stats.getPrepareStatementCount()).as("SQL statements (N+1 check)").isEqualTo(1);
		assertThat(races.findWithDetailsById(999_999L)).isEmpty();
	}

	private Race saveRace(int round, String name, String date, Integer meetingKey) {
		var race = new Race(season2024, (short) round, monza, name, LocalDate.parse(date));
		race.setOpenf1MeetingKey(meetingKey);
		return races.save(race);
	}

}

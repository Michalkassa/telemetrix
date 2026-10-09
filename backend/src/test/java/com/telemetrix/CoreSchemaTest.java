package com.telemetrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// The V1 core schema, tested with plain SQL (entities come in Step 7). Each test is rolled back.
@RepositoryTest
class CoreSchemaTest {

	static final OffsetDateTime RACE_START = OffsetDateTime.parse("2024-09-01T13:00:00Z");

	@Autowired
	JdbcTemplate jdbc;

	long monzaId;

	long italianGpId;

	@BeforeEach
	void seedSeasonCircuitAndRace() {
		jdbc.update("insert into seasons (year) values (2024)");
		monzaId = insertCircuit("monza", 39);
		italianGpId = insertRace(2024, 16, monzaId, 1245);
	}

	@Test
	void storesASeasonRaceAndSessionChain() {
		insertSession(italianGpId, 9590, "Race", RACE_START, RACE_START.plusHours(2));

		var row = jdbc.queryForMap("""
				select r.name as race, c.name as circuit, s.name as session, r.season_year
				from sessions s
				join races r on r.id = s.race_id
				join circuits c on c.id = r.circuit_id
				where s.openf1_session_key = 9590
				""");

		assertThat(row).containsEntry("race", "Italian Grand Prix")
				.containsEntry("circuit", "Autodromo Nazionale di Monza")
				.containsEntry("session", "Race")
				.containsEntry("season_year", 2024); // SMALLINT comes back as Integer
	}

	@Test
	void rejectsADuplicateSessionKey() {
		insertSession(italianGpId, 9590, "Race", RACE_START, RACE_START.plusHours(2));

		assertThatThrownBy(() -> insertSession(italianGpId, 9590, "Sprint", RACE_START, RACE_START.plusHours(1)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsASessionWhoseRaceDoesNotExist() {
		assertThatThrownBy(() -> insertSession(999_999, 9590, "Race", RACE_START, RACE_START.plusHours(2)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsASessionThatEndsBeforeItStarts() {
		assertThatThrownBy(() -> insertSession(italianGpId, 9590, "Race", RACE_START, RACE_START.minusMinutes(1)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsTwoRacesInTheSameRoundOfASeason() {
		long spaId = insertCircuit("spa", 7);

		assertThatThrownBy(() -> insertRace(2024, 16, spaId, null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsASeasonBeforeTheChampionshipExisted() {
		assertThatThrownBy(() -> jdbc.update("insert into seasons (year) values (1949)"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// One violation per test: after an error Postgres aborts the rest of the (rolled-back) transaction.
	@Test
	void driversAreUniqueBySourceRef() {
		jdbc.update("""
				insert into drivers (ref, code, permanent_number, given_name, family_name, nationality)
				values ('max_verstappen', 'VER', 1, 'Max', 'Verstappen', 'Dutch')""");

		assertThatThrownBy(() -> jdbc.update(
				"insert into drivers (ref, given_name, family_name) values ('max_verstappen', 'Max', 'V')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void constructorsAreUniqueBySourceRef() {
		jdbc.update("insert into constructors (ref, name, nationality) values ('red_bull', 'Red Bull', 'Austrian')");

		assertThatThrownBy(() -> jdbc.update("insert into constructors (ref, name) values ('red_bull', 'RBR')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long insertCircuit(String ref, Integer openf1Key) {
		return jdbc.queryForObject("""
				insert into circuits (ref, name, locality, country, openf1_circuit_key)
				values (?, ?, 'Monza', 'Italy', ?) returning id""", Long.class,
				ref, ref.equals("monza") ? "Autodromo Nazionale di Monza" : "Circuit " + ref, openf1Key);
	}

	private long insertRace(int year, int round, long circuitId, Integer meetingKey) {
		return jdbc.queryForObject("""
				insert into races (season_year, round, circuit_id, name, race_date, openf1_meeting_key)
				values (?, ?, ?, 'Italian Grand Prix', date '2024-09-01', ?) returning id""", Long.class,
				year, round, circuitId, meetingKey);
	}

	private void insertSession(long raceId, int sessionKey, String name, OffsetDateTime start, OffsetDateTime end) {
		jdbc.update("""
				insert into sessions (race_id, openf1_session_key, name, starts_at, ends_at)
				values (?, ?, ?, ?, ?)""", raceId, sessionKey, name, start, end);
	}

}

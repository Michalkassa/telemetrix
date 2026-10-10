package com.telemetrix.ingestion.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

// The whole history import (a Spring Batch job) against the Testcontainers database, fed a trimmed
// copy of the real Jolpica dump: British GP 1950, Australian GP 2008, Bahrain GP 2024, and the
// cancelled 2026 Saudi Arabian GP.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HistoryImportJobTest {

	@Autowired
	JobOperator jobOperator;

	@Autowired
	Job jolpicaHistoryImport;

	@Autowired
	JdbcTemplate jdbc;

	Path fixtureDump;

	long runId; // Batch remembers finished runs: each test uses its own

	@BeforeEach
	void cleanDatabase() throws Exception {
		runId = System.nanoTime();
		jdbc.execute("""
				truncate seasons, circuits, drivers, constructors, races, sessions, race_entries, session_results,
				         lap_times, pit_stops restart identity cascade""");
		fixtureDump = new ClassPathResource("fixtures/jolpica/dump").getFile().toPath();
	}

	@Test
	void importsEveryTableAndSkipsTheCancelledRound() throws Exception {
		JobExecution run = run(fixtureDump, runId);

		assertThat(run.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(counts()).containsExactlyInAnyOrderEntriesOf(Map.of(
				"seasons", 4L, "circuits", 4L, "drivers", 63L, "constructors", 22L, "races", 3L,
				"race_entries", 65L, "session_results", 155L, "lap_times", 190L, "pit_stops", 43L));
		assertThat(jdbc.queryForObject("select count(*) from races where season_year = 2026", Long.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from session_results where best_lap_ms is not null",
				Long.class)).isEqualTo(127);
	}

	@Test
	void keepsWhyDriversRetired() throws Exception {
		run(fixtureDump, runId);

		var glock = result(2008, "glock", "R");
		assertThat(glock).containsEntry("status", "RETIRED_INCIDENT").containsEntry("status_detail", "Accident");
		var kubica = result(2008, "kubica", "R");
		assertThat(kubica).containsEntry("status", "RETIRED_INCIDENT").containsEntry("status_detail", "Collision");
	}

	@Test
	void storesTheWinnersResultQualifyingLapsAndPitStops() throws Exception {
		run(fixtureDump, runId);

		var max = result(2024, "max_verstappen", "R");
		assertThat(max).containsEntry("position", 1).containsEntry("grid", 1).containsEntry("laps_completed", 57)
				.containsEntry("time_ms", 5_504_742L).containsEntry("status", "FINISHED");
		assertThat(((Number) max.get("points")).doubleValue()).isEqualTo(26.0);

		var pole = jdbc.queryForMap("""
				select q.position, q.q1_ms, q.q2_ms, q.q3_ms from qualifying q
				join races r on r.id = q.race_id join drivers d on d.id = q.driver_id
				where r.season_year = 2024 and r.round = 1 and d.ref = 'max_verstappen'""");
		assertThat(pole).containsEntry("position", 1).containsEntry("q1_ms", 90_031L).containsEntry("q2_ms", 89_374L)
				.containsEntry("q3_ms", 89_179L);
		assertThat(jdbc.queryForObject("""
				select q.position from qualifying q join races r on r.id = q.race_id join drivers d on d.id = q.driver_id
				where r.season_year = 2024 and d.ref = 'hamilton'""", Integer.class)).isEqualTo(9);

		assertThat(jdbc.queryForObject("""
				select car_number from race_entries e join races r on r.id = e.race_id join drivers d on d.id = e.driver_id
				where r.season_year = 2024 and d.ref = 'max_verstappen'""", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForList("""
				select l.lap from lap_times l join race_entries e on e.id = l.race_entry_id
				join races r on r.id = e.race_id join drivers d on d.id = e.driver_id
				where r.season_year = 2024 and d.ref = 'max_verstappen' and l.session_type = 'R' and l.lap <= 3
				order by l.lap""", Integer.class)).containsExactly(1, 2, 3);
		assertThat(jdbc.queryForObject("select count(*) from pit_stops where lap is null or lane_duration_ms is null",
				Long.class)).isZero();
	}

	@Test
	void aSprintsPitStopsAreNotTheRaces() throws Exception {
		// the real dump's rows for Hamilton at Miami 2024: one stop in the race (lap 26), two in the sprint
		run(new ClassPathResource("fixtures/jolpica/dump-sprint").getFile().toPath(), runId);

		assertThat(jdbc.queryForList("select stop_number, lap, lane_duration_ms from pit_stops"))
				.containsExactly(Map.of("stop_number", 1, "lap", 26, "lane_duration_ms", 22107L));
	}

	@Test
	void importingTwiceGivesIdenticalRows() throws Exception {
		run(fixtureDump, runId);
		var first = counts();

		JobExecution second = run(fixtureDump, runId + 1);

		assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(counts()).isEqualTo(first);
	}

	@Test
	void aRaceFromTheCalendarImportKeepsItsOpenF1Link() throws Exception {
		jdbc.update("insert into seasons (year) values (2024)");
		jdbc.update("insert into circuits (ref, name) values ('bahrain', 'Bahrain')");
		jdbc.update("""
				insert into races (season_year, round, circuit_id, name, race_date, openf1_meeting_key)
				select 2024, 1, id, 'Bahrain GP', date '2024-03-02', 1229 from circuits where ref = 'bahrain'""");

		run(fixtureDump, runId);

		var race = jdbc.queryForMap("select name, openf1_meeting_key from races where season_year = 2024 and round = 1");
		assertThat(race).containsEntry("name", "Bahrain Grand Prix").containsEntry("openf1_meeting_key", 1229);
		assertThat(counts()).containsEntry("races", 3L);
	}

	@Test
	void restartsWhereItStoppedAfterAFailure(@TempDir Path work) throws Exception {
		Path dump = copyOf(fixtureDump, work);
		Path laps = dump.resolve("formula_one_lap.csv");
		byte[] goodLaps = Files.readAllBytes(laps);
		appendRaceLapNumberedZero(dump); // well-formed, but the database rejects lap 0 in the lapTimes step

		JobExecution failed = run(dump, runId);
		assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(failed.getStepExecutions()).filteredOn(s -> s.getStepName().equals("lapTimes"))
				.extracting(StepExecution::getStatus).containsExactly(BatchStatus.FAILED);

		Files.write(laps, goodLaps);
		JobExecution restarted = run(dump, runId); // same parameters: a restart of the failed run

		assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(restarted.getJobInstance().getInstanceId()).isEqualTo(failed.getJobInstance().getInstanceId());
		assertThat(restarted.getStepExecutions()).extracting(StepExecution::getStepName)
				.doesNotContain("seasons", "drivers", "races", "raceEntries", "sessionResults", "bestLaps")
				.contains("lapTimes", "pitStops");
		assertThat(counts()).containsEntry("lap_times", 190L).containsEntry("session_results", 155L)
				.containsEntry("pit_stops", 43L);
	}

	// A lap row (columns in the file's own header order) for some race session entry, with lap number 0.
	private static void appendRaceLapNumberedZero(Path dump) throws Exception {
		var raceSessions = DumpCsv.readAll(dump.resolve("formula_one_session.csv")).stream()
				.filter(r -> r.get("type").equals("R")).map(r -> r.get("id")).toList();
		String raceEntry = DumpCsv.readAll(dump.resolve("formula_one_sessionentry.csv")).stream()
				.filter(r -> raceSessions.contains(r.get("session_id"))).map(r -> r.get("id")).findFirst().orElseThrow();
		Path laps = dump.resolve("formula_one_lap.csv");
		String[] header = Files.readAllLines(laps).getFirst().split(",");
		var row = Map.of("id", "999999999", "api_id", "lap_test", "is_deleted", "f", "is_entry_fastest_lap", "f",
				"number", "0", "session_entry_id", raceEntry, "time", "00:01:40.000");
		String line = java.util.Arrays.stream(header).map(column -> row.getOrDefault(column, ""))
				.collect(java.util.stream.Collectors.joining(","));
		Files.writeString(laps, line + "\n", StandardOpenOption.APPEND);
	}

	private JobExecution run(Path dumpDir, long run) throws Exception {
		return jobOperator.start(jolpicaHistoryImport, HistoryImportService.parameters(dumpDir, run));
	}

	private Map<String, Long> counts() {
		var counts = new java.util.TreeMap<String, Long>();
		for (String table : new String[] { "seasons", "circuits", "drivers", "constructors", "races", "race_entries",
				"session_results", "lap_times", "pit_stops" }) {
			counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
		}
		return counts;
	}

	private Map<String, Object> result(int year, String driverRef, String sessionType) {
		return jdbc.queryForMap("""
				select s.position, s.grid, s.points, s.laps_completed, s.time_ms, s.status, s.status_detail
				from session_results s join race_entries e on e.id = s.race_entry_id
				join races r on r.id = e.race_id join drivers d on d.id = e.driver_id
				where r.season_year = ? and r.round = 1 and d.ref = ? and s.session_type = ?""", year, driverRef,
				sessionType);
	}

	private static Path copyOf(Path dir, Path target) throws Exception {
		Path copy = target.resolve("dump");
		Files.createDirectories(copy);
		try (var files = Files.list(dir)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				Files.copy(file, copy.resolve(file.getFileName()));
			}
		}
		return copy;
	}

}

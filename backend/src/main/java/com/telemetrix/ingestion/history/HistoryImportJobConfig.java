package com.telemetrix.ingestion.history;

import com.telemetrix.result.ResultStatus;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

// The Jolpica history import as a restartable Spring Batch job (docs/adr/0003, STEPS.md Step 10).
//
// Job parameter `dumpDir`: a folder with the unzipped CSV dump. Each chunk step streams one CSV file,
// maps rows to natural keys through the DumpIndex, and upserts (INSERT ... ON CONFLICT), so re-running
// is safe. Batch records each step's progress: a failed run restarted with the same parameters skips
// the finished steps and resumes the failed one from its last committed chunk.
@Configuration(proxyBeanMethods = false)
class HistoryImportJobConfig {

	static final String JOB_NAME = "jolpicaHistoryImport";

	private static final int CHUNK_SIZE = 1000;

	private static final String DUMP_DIR = "#{jobParameters['dumpDir']}";

	private final JobRepository jobRepository;

	private final PlatformTransactionManager transactionManager;

	private final DataSource dataSource;

	private final DumpIndex index;

	HistoryImportJobConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			DataSource dataSource, DumpIndex index) {
		this.jobRepository = jobRepository;
		this.transactionManager = transactionManager;
		this.dataSource = dataSource;
		this.index = index;
	}

	@Bean
	Job jolpicaHistoryImport(ApplicationEventPublisher events, Step indexDump, Step seasons, Step circuits, Step drivers, Step constructors, Step races,
			Step raceEntries, Step linkEntries, Step sessionResults, Step bestLaps, Step lapTimes, Step pitStops) {
		return new JobBuilder(JOB_NAME, jobRepository)
				.start(indexDump)
				.next(seasons)
				.next(circuits)
				.next(drivers)
				.next(constructors)
				.next(races)
				.next(raceEntries)
				.next(linkEntries)
				.next(sessionResults)
				.next(bestLaps)
				.next(lapTimes)
				.next(pitStops)
				.listener(new JobExecutionListener() {

					@Override
					public void afterJob(JobExecution run) {
						if (run.getStatus() == BatchStatus.COMPLETED) {
							events.publishEvent(new HistoryImported(run.getId()));
						}
					}

				})
				.build();
	}

	// ---- in-memory steps: run again on every restart, because memory doesn't survive one ----------

	@Bean
	Step indexDump() {
		Tasklet load = (contribution, chunk) -> {
			index.load(Path.of((String) chunk.getStepContext().getJobParameters().get("dumpDir")));
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("indexDump", jobRepository).tasklet(load, transactionManager)
				.allowStartIfComplete(true)
				.build();
	}

	@Bean
	Step linkEntries(JdbcTemplate jdbc) {
		Tasklet link = (contribution, chunk) -> {
			Map<String, Long> ids = new HashMap<>();
			jdbc.query("""
					select e.id, r.season_year, r.round, d.ref as driver_ref, c.ref as team_ref, e.car_number
					from race_entries e
					join races r on r.id = e.race_id
					join drivers d on d.id = e.driver_id
					join constructors c on c.id = e.constructor_id""", rs -> {
				ids.put(DumpIndex.entryKey(rs.getInt("season_year"), rs.getInt("round"), rs.getString("driver_ref"),
						rs.getString("team_ref"), (Integer) rs.getObject("car_number")), rs.getLong("id"));
			});
			index.linkEntries(ids);
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("linkEntries", jobRepository).tasklet(link, transactionManager)
				.allowStartIfComplete(true)
				.build();
	}

	// ---- reference data ------------------------------------------------------------------------

	@Bean
	Step seasons(ItemReader<Map<String, String>> seasonRows) {
		return chunkStep("seasons", seasonRows, row -> Map.of("year", Integer.valueOf(row.get("year"))), """
				insert into seasons (year) values (:year) on conflict (year) do nothing""");
	}

	@Bean
	Step circuits(ItemReader<Map<String, String>> circuitRows) {
		return chunkStep("circuits", circuitRows, row -> params(
				"ref", row.get("reference"), "name", row.get("name"),
				"locality", JolpicaValues.text(row.get("locality")), "country", JolpicaValues.text(row.get("country")),
				"latitude", JolpicaValues.decimal(row.get("latitude")),
				"longitude", JolpicaValues.decimal(row.get("longitude"))), """
				insert into circuits (ref, name, locality, country, latitude, longitude)
				values (:ref, :name, :locality, :country, :latitude, :longitude)
				on conflict (ref) do update set name = excluded.name, locality = excluded.locality,
				    country = excluded.country, latitude = excluded.latitude, longitude = excluded.longitude""");
	}

	@Bean
	Step drivers(ItemReader<Map<String, String>> driverRows) {
		return chunkStep("drivers", driverRows, row -> params(
				"ref", row.get("reference"), "code", JolpicaValues.text(row.get("abbreviation")),
				"number", JolpicaValues.integer(row.get("permanent_car_number")),
				"givenName", row.get("forename"), "familyName", row.get("surname"),
				"dateOfBirth", JolpicaValues.date(row.get("date_of_birth")),
				"nationality", JolpicaValues.text(row.get("nationality"))), """
				insert into drivers (ref, code, permanent_number, given_name, family_name, date_of_birth, nationality)
				values (:ref, :code, :number, :givenName, :familyName, :dateOfBirth, :nationality)
				on conflict (ref) do update set code = excluded.code, permanent_number = excluded.permanent_number,
				    given_name = excluded.given_name, family_name = excluded.family_name,
				    date_of_birth = excluded.date_of_birth, nationality = excluded.nationality""");
	}

	@Bean
	Step constructors(ItemReader<Map<String, String>> teamRows) {
		return chunkStep("constructors", teamRows, row -> params(
				"ref", row.get("reference"), "name", row.get("name"),
				"nationality", JolpicaValues.text(row.get("nationality"))), """
				insert into constructors (ref, name, nationality) values (:ref, :name, :nationality)
				on conflict (ref) do update set name = excluded.name, nationality = excluded.nationality""");
	}

	// A race keeps its id and its OpenF1 link (Step 9) on conflict; only calendar fields change.
	@Bean
	Step races(ItemReader<Map<String, String>> roundRows) {
		return chunkStep("races", roundRows, row -> {
			var round = index.round(row.get("id"));
			if (round == null || !round.importable()) {
				return null; // cancelled
			}
			return params("year", round.year(), "round", round.number(), "circuitRef", round.circuitRef(),
					"name", row.get("name"), "date", JolpicaValues.date(row.get("date")));
		}, """
				insert into races (season_year, round, circuit_id, name, race_date)
				select cast(:year as smallint), cast(:round as smallint), c.id, cast(:name as varchar),
				       cast(:date as date)
				from circuits c where c.ref = :circuitRef
				on conflict (season_year, round) do update set circuit_id = excluded.circuit_id,
				    name = excluded.name, race_date = excluded.race_date""");
	}

	// ---- per-entry data --------------------------------------------------------------------------

	@Bean
	Step raceEntries(ItemReader<Map<String, String>> roundEntryRows) {
		return chunkStep("raceEntries", roundEntryRows, row -> {
			var entry = index.entry(row.get("id"));
			if (entry == null || !entry.round().importable()) {
				return null;
			}
			return params("year", entry.round().year(), "round", entry.round().number(),
					"driverRef", entry.driverRef(), "teamRef", entry.teamRef(), "car", entry.carNumber());
		}, """
				insert into race_entries (race_id, driver_id, constructor_id, car_number)
				select r.id, d.id, c.id, cast(:car as smallint)
				from races r, drivers d, constructors c
				where r.season_year = :year and r.round = :round and d.ref = :driverRef and c.ref = :teamRef
				on conflict on constraint race_entries_unique do nothing""");
	}

	@Bean
	Step sessionResults(ItemReader<Map<String, String>> sessionEntryRows) {
		return chunkStep("sessionResults", sessionEntryRows, row -> {
			Long entry = index.ourEntryId(row.get("round_entry_id"));
			var session = index.session(row.get("session_id"));
			if (entry == null || session == null) {
				return null;
			}
			ResultStatus status = ResultStatus.fromJolpicaCode(row.get("status"));
			return params("entry", entry, "type", session.type(), "number", session.number(),
					"position", JolpicaValues.integer(row.get("position")),
					"grid", JolpicaValues.integer(row.get("grid")),
					"points", JolpicaValues.decimal(row.get("points")),
					"laps", JolpicaValues.integer(row.get("laps_completed")),
					"timeMs", JolpicaValues.millis(row.get("time")),
					"status", status == null ? null : status.name(),
					"detail", JolpicaValues.text(row.get("detail")),
					"classified", JolpicaValues.bool(row.get("is_classified")),
					"fastestLapRank", JolpicaValues.integer(row.get("fastest_lap_rank")));
		}, """
				insert into session_results (race_entry_id, session_type, session_number, position, grid, points,
				    laps_completed, time_ms, status, status_detail, classified, fastest_lap_rank)
				values (:entry, :type, :number, :position, :grid, :points, :laps, :timeMs, :status, :detail,
				    :classified, :fastestLapRank)
				on conflict (race_entry_id, session_type, session_number) do update set
				    position = excluded.position, grid = excluded.grid, points = excluded.points,
				    laps_completed = excluded.laps_completed, time_ms = excluded.time_ms, status = excluded.status,
				    status_detail = excluded.status_detail, classified = excluded.classified,
				    fastest_lap_rank = excluded.fastest_lap_rank""");
	}

	// Each entry's best lap per session (qualifying times come from here).
	@Bean
	Step bestLaps(ItemReader<Map<String, String>> bestLapRows) {
		return chunkStep("bestLaps", bestLapRows, row -> {
			if (!"t".equals(row.get("is_entry_fastest_lap"))) {
				return null;
			}
			var target = sessionOf(row.get("session_entry_id"));
			Long ms = JolpicaValues.millis(row.get("time"));
			if (target == null || ms == null) {
				return null;
			}
			target.put("ms", ms);
			return target;
		}, """
				update session_results set best_lap_ms = :ms
				where race_entry_id = :entry and session_type = :type and session_number = :number""");
	}

	@Bean
	Step lapTimes(ItemReader<Map<String, String>> lapRows) {
		return chunkStep("lapTimes", lapRows, row -> {
			if (JolpicaValues.blank(row.get("number")) || "t".equals(row.get("is_deleted"))) {
				return null;
			}
			var target = sessionOf(row.get("session_entry_id"));
			if (target == null || !("R".equals(target.get("type")) || "SR".equals(target.get("type")))) {
				return null;
			}
			target.put("lap", JolpicaValues.integer(row.get("number")));
			target.put("position", JolpicaValues.integer(row.get("position")));
			target.put("ms", JolpicaValues.millis(row.get("time")));
			return target;
		}, """
				insert into lap_times (race_entry_id, session_type, lap, position, time_ms)
				values (:entry, :type, :lap, :position, :ms)
				on conflict (race_entry_id, session_type, lap) do update set
				    position = excluded.position, time_ms = excluded.time_ms""");
	}

	@Bean
	Step pitStops(ItemReader<Map<String, String>> pitStopRows) {
		return chunkStep("pitStops", pitStopRows, row -> {
			var target = sessionOf(row.get("session_entry_id"));
			// pit_stops are the Grand Prix's: a sprint's stops are numbered from 1 too, and would replace them
			if (target == null || !"R".equals(target.get("type"))) {
				return null;
			}
			return params("entry", target.get("entry"), "stop", JolpicaValues.integer(row.get("number")),
					"lap", index.pitLapNumber(row.get("lap_id")),
					"laneMs", JolpicaValues.millis(row.get("duration")),
					"localTime", JolpicaValues.time(row.get("local_timestamp")));
		}, """
				insert into pit_stops (race_entry_id, stop_number, lap, lane_duration_ms, local_time)
				values (:entry, :stop, :lap, :laneMs, :localTime)
				on conflict (race_entry_id, stop_number) do update set lap = excluded.lap,
				    lane_duration_ms = excluded.lane_duration_ms, local_time = excluded.local_time""");
	}

	// session entry -> {entry: our race_entries id, type, number}, or null if it isn't ours
	private Map<String, Object> sessionOf(String sessionEntryId) {
		var sessionEntry = index.sessionEntry(sessionEntryId);
		if (sessionEntry == null) {
			return null;
		}
		Long entry = index.ourEntryId(sessionEntry.roundEntryId());
		var session = index.session(sessionEntry.sessionId());
		if (entry == null || session == null) {
			return null;
		}
		return params("entry", entry, "type", session.type(), "number", session.number());
	}

	// ---- one restartable reader per file, bound to this run's dump folder ----------------------------

	@Bean
	@StepScope
	FlatFileItemReader<Map<String, String>> seasonRows(@Value(DUMP_DIR) String dir) {
		return DumpCsv.reader(Path.of(dir, "formula_one_season.csv"), "seasonRows");
	}

	@Bean
	@StepScope
	FlatFileItemReader<Map<String, String>> circuitRows(@Value(DUMP_DIR) String dir) {
		return DumpCsv.reader(Path.of(dir, "formula_one_circuit.csv"), "circuitRows");
	}

	@Bean
	@StepScope
	FlatFileItemReader<Map<String, String>> driverRows(@Value(DUMP_DIR) String dir) {
		return DumpCsv.reader(Path.of(dir, "formula_one_driver.csv"), "driverRows");
	}

	@Bean
	@StepScope
	FlatFileItemReader<Map<String, String>> teamRows(@Value(DUMP_DIR) String dir) {
		return DumpCsv.reader(Path.of(dir, "formula_one_team.csv"), "teamRows");
	}

	@Bean
	@StepScope
	FlatFileItemReader<Map<String, String>> roundRows(@Value(DUMP_DIR) String dir) {
		return DumpCsv.reader(Path.of(dir, "formula_one_round.csv"), "roundRows");
	}

	@Bean
	@StepScope
	FlatFileItemReader<Map<String, String>> roundEntryRows(@Value(DUMP_DIR) String dir) {
		return DumpCsv.reader(Path.of(dir, "formula_one_roundentry.csv"), "roundEntryRows");
	}

	@Bean
	@StepScope
	FlatFileItemReader<Map<String, String>> sessionEntryRows(@Value(DUMP_DIR) String dir) {
		return DumpCsv.reader(Path.of(dir, "formula_one_sessionentry.csv"), "sessionEntryRows");
	}

	@Bean
	@StepScope
	FlatFileItemReader<Map<String, String>> bestLapRows(@Value(DUMP_DIR) String dir) {
		return DumpCsv.reader(Path.of(dir, "formula_one_lap.csv"), "bestLapRows");
	}

	@Bean
	@StepScope
	FlatFileItemReader<Map<String, String>> lapRows(@Value(DUMP_DIR) String dir) {
		return DumpCsv.reader(Path.of(dir, "formula_one_lap.csv"), "lapRows");
	}

	@Bean
	@StepScope
	FlatFileItemReader<Map<String, String>> pitStopRows(@Value(DUMP_DIR) String dir) {
		return DumpCsv.reader(Path.of(dir, "formula_one_pitstop.csv"), "pitStopRows");
	}

	// ---- helpers ---------------------------------------------------------------------------------

	private Step chunkStep(String name, ItemReader<Map<String, String>> reader,
			ItemProcessor<Map<String, String>, Map<String, Object>> processor, String upsertSql) {
		var writer = new JdbcBatchItemWriterBuilder<Map<String, Object>>()
				.dataSource(dataSource)
				.sql(upsertSql)
				.itemSqlParameterSourceProvider(MapSqlParameterSource::new)
				.assertUpdates(false) // a row whose race/driver is unknown simply matches nothing
				.build();
		writer.afterPropertiesSet();
		return new StepBuilder(name, jobRepository).<Map<String, String>, Map<String, Object>>chunk(CHUNK_SIZE)
				.reader(reader)
				.processor(processor)
				.writer(writer)
				.transactionManager(transactionManager)
				.build();
	}

	// Map.of() rejects nulls; source values are often missing
	private static Map<String, Object> params(Object... keysAndValues) {
		Map<String, Object> params = new HashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			params.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return params;
	}

}

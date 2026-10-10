package com.telemetrix.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.ingestion.RaceImported;
import com.telemetrix.ingestion.history.HistoryImportService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

// The analytics view is refreshed when the data under it changes: after the history import (the recorded
// dump: 23 + 22 + 20 Grand Prix results, British GP 1950, Australian GP 2008, Bahrain GP 2024) and after a
// race import (which brings its weather).
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DriverStatsRefreshTest {

	@Autowired
	JobOperator jobOperator;

	@Autowired
	Job jolpicaHistoryImport;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void importHistory() throws Exception {
		jdbc.execute("""
				truncate seasons, circuits, drivers, constructors, races, sessions, race_entries, session_results,
				         lap_times, pit_stops, championship_snapshots restart identity cascade""");
		jdbc.execute("refresh materialized view driver_race_results");
		jdbc.update("insert into championship_snapshots values (1999, 1, 'DRIVER', 'old', 1, 10, 1)");
		var dump = new ClassPathResource("fixtures/jolpica/dump").getFile().toPath();
		jobOperator.start(jolpicaHistoryImport, HistoryImportService.parameters(dump, System.nanoTime()));
	}

	@Test
	void theHistoryImportRefreshesTheViewAndDropsStaleStandings() {
		assertThat(jdbc.queryForObject("select count(*) from driver_race_results", Long.class)).isEqualTo(65);
		assertThat(jdbc.queryForObject("select count(*) from championship_snapshots", Long.class)).isZero();
	}

	@Test
	void aRaceImportRefreshesTheViewWithItsWeather() {
		long race = jdbc.queryForObject("select id from races where season_year = 2024", Long.class);
		long session = jdbc.queryForObject("""
				insert into sessions (race_id, name, starts_at) values (?, 'Race', '2024-03-02T15:00:00Z') returning id""",
				Long.class, race);
		jdbc.update("insert into weather_data (session_id, recorded_at, rainfall) values (?, '2024-03-02T15:30:00Z', true)",
				session);

		events.publishEvent(new RaceImported(race, List.of(session)));

		assertThat(jdbc.queryForObject("select bool_and(wet) from driver_race_results where race_id = ?", Boolean.class,
				race)).isTrue();
	}

}

package com.telemetrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.ingestion.TrackStatusSourceFactory;
import com.telemetrix.ingestion.f1archive.F1ArchiveProperties;
import com.telemetrix.ingestion.jolpica.JolpicaProperties;
import com.telemetrix.ingestion.openf1.OpenF1Properties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// The whole app, started as in production, against a real PostgreSQL.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplicationIntegrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	OpenF1Properties openF1;

	@Autowired
	JolpicaProperties jolpica;

	@Autowired
	F1ArchiveProperties f1Archive;

	@Autowired
	TrackStatusSourceFactory trackStatusSources;

	@Test
	void contextLoads() {
	}

	@Test
	void flywayManagesTheSchema() {
		// Flyway records every migration it applies in this table; it exists once Flyway has run.
		Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history", Integer.class);

		assertThat(applied).isNotNull();
	}

	@Test
	void openF1DefaultsTargetTheRealApiWithinItsRateLimit() {
		assertThat(openF1.baseUrl()).isEqualTo("https://api.openf1.org/v1");
		// free tier: 3 requests/s and 30 requests/min, so at most one request every 2 s
		assertThat(openF1.minRequestInterval()).isEqualTo(Duration.ofSeconds(2));
		assertThat(openF1.maxAttempts()).isGreaterThan(1);
	}

	@Test
	void jolpicaDefaultsTargetTheRealApiWithinItsRateLimit() {
		assertThat(jolpica.baseUrl()).isEqualTo("https://api.jolpi.ca/ergast/f1");
		// unauthenticated: 500 requests/hour sustained, so at most one request every 7.2 s
		assertThat(jolpica.minRequestInterval()).isGreaterThanOrEqualTo(Duration.ofMillis(7200));
	}

	@Test
	void theF1ArchiveIsUsedForTrackStatusByDefaultAndPolitely() {
		assertThat(f1Archive.enabled()).isTrue();
		assertThat(f1Archive.baseUrl()).isEqualTo("https://livetiming.formula1.com/static");
		assertThat(f1Archive.minRequestInterval()).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
		assertThat(trackStatusSources).isNotNull();
	}

}

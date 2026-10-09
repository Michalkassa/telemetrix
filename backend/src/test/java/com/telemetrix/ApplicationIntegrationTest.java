package com.telemetrix;

import static org.assertj.core.api.Assertions.assertThat;

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

}

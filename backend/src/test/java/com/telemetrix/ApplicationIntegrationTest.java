package com.telemetrix;

import static org.assertj.core.api.Assertions.assertThat;

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

	@Test
	void contextLoads() {
	}

	@Test
	void flywayManagesTheSchema() {
		// Flyway records every migration it applies in this table; it exists once Flyway has run.
		Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history", Integer.class);

		assertThat(applied).isNotNull();
	}

}

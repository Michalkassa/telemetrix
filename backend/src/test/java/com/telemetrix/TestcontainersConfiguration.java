package com.telemetrix;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// A throwaway PostgreSQL in Docker for tests, so they never touch a dev database.
// Tests that need a database add @Import(TestcontainersConfiguration.class);
// @ServiceConnection points spring.datasource.* at the container.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:16"); // same version as docker-compose.yml
	}

}

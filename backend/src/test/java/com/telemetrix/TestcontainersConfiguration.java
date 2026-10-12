package com.telemetrix;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

// A throwaway PostgreSQL and Redis in Docker for tests, so they never touch a dev database or cache.
// Tests that need them add @Import(TestcontainersConfiguration.class); @ServiceConnection points
// spring.datasource.* and spring.data.redis.* at the containers.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/** Same image as docker-compose.yml. */
	public static final String REDIS = "redis:7.4-alpine";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:16"); // same version as docker-compose.yml
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redis() {
		return new GenericContainer<>(REDIS).withExposedPorts(6379);
	}

}

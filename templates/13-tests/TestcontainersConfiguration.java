// TEMPLATE 13e — A real Postgres for tests, in Docker
// Where it goes: src/test/java/com/telemetrix/TestcontainersConfiguration.java
//
// Tests that need a database add  @Import(TestcontainersConfiguration.class).
// On first use Testcontainers starts a postgres:16 container (Docker must be running) and
// @ServiceConnection points spring.datasource.* at it — no URL/password configuration needed.
// The container is shared across the test run and removed afterwards.

package com.telemetrix.library;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16"); // same version as docker-compose.yml
    }
}

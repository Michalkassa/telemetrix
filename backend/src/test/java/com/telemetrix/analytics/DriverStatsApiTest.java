package com.telemetrix.analytics;

import com.telemetrix.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

// Outer test: GET /api/drivers/{id}/stats returns the hand-worked numbers of the seeded mini-dataset.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
// (an inline @Sql statement is one line)
@Sql(statements = "truncate seasons, circuits, drivers, constructors, races, sessions, race_entries, session_results, "
		+ "lap_times, pit_stops, championship_snapshots restart identity cascade")
@Sql("/analytics/mini-dataset.sql")
class DriverStatsApiTest {

	@Autowired
	RestTestClient http;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void afterAnImport() {
		jdbc.execute("refresh materialized view driver_race_results");
	}

	@Test
	void hamiltonsStats() {
		http.get().uri("/api/drivers/hamilton/stats").exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.driver.code").isEqualTo("HAM")
				.jsonPath("$.career.starts").isEqualTo(3)
				.jsonPath("$.career.wins").isEqualTo(2)
				.jsonPath("$.career.podiums").isEqualTo(3)
				.jsonPath("$.career.poles").isEqualTo(1)
				.jsonPath("$.career.points").isEqualTo(69.0)
				.jsonPath("$.wetDry.wet.wins").isEqualTo(1)
				.jsonPath("$.tracks[0].circuit").isEqualTo("bahrain")
				.jsonPath("$.pitStops.stops").isEqualTo(3)
				.jsonPath("$.tyres[0].compound").isEqualTo("HARD");
	}

	@Test
	void anUnknownDriverIsNotFound() {
		http.get().uri("/api/drivers/nobody/stats").exchange().expectStatus().isNotFound();
	}

	@Test
	void theDirectoryAndARaceLog() {
		http.get().uri("/api/drivers").exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.length()").isEqualTo(4)
				.jsonPath("$[1].ref").isEqualTo("hamilton")
				.jsonPath("$[1].wins").isEqualTo(2);
		http.get().uri("/api/drivers/leclerc/races").exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.length()").isEqualTo(3)
				.jsonPath("$[1].statusDetail").isEqualTo("Accident")
				.jsonPath("$[1].teamRef").isEqualTo("ferrari");
		http.get().uri("/api/drivers/nobody/races").exchange().expectStatus().isNotFound();
	}

}

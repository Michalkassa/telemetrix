package com.telemetrix.analytics;

import com.telemetrix.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

// Outer test: GET /api/constructors/{id}/stats returns the hand-worked numbers of the seeded mini-dataset.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
// (an inline @Sql statement is one line)
@Sql(statements = "truncate seasons, circuits, drivers, constructors, races, sessions, race_entries, session_results, "
		+ "lap_times, pit_stops, championship_snapshots restart identity cascade")
@Sql("/analytics/constructor-dataset.sql")
class ConstructorStatsApiTest {

	@Autowired
	RestTestClient http;

	@Test
	void mercedesIn2024() {
		http.get().uri("/api/constructors/mercedes/stats?season=2024").exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.constructor.name").isEqualTo("Mercedes")
				.jsonPath("$.season").isEqualTo(2024)
				.jsonPath("$.reliability.dnfs").isEqualTo(2)
				.jsonPath("$.reliability.causes[0].cause").isEqualTo("Engine")
				.jsonPath("$.pitStops.medianLaneSeconds").isEqualTo(23.0)
				.jsonPath("$.pitStops.rank").isEqualTo(2)
				.jsonPath("$.trend.rounds.length()").isEqualTo(2)
				.jsonPath("$.strategies[1].stops").isEqualTo(2);
	}

	@Test
	void theLatestSeasonByDefault() {
		http.get().uri("/api/constructors/mercedes/stats").exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.season").isEqualTo(2024);
	}

	@Test
	void anUnknownTeamIsNotFound() {
		http.get().uri("/api/constructors/nobody/stats").exchange().expectStatus().isNotFound();
	}

}

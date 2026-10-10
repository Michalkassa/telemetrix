package com.telemetrix.compare;

import com.telemetrix.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

// Outer test: GET /api/compare/... over the seeded dataset, with its validation.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
// (an inline @Sql statement is one line)
@Sql(statements = "truncate seasons, circuits, drivers, constructors, races, sessions, race_entries, session_results, "
		+ "lap_times, pit_stops, championship_snapshots restart identity cascade")
@Sql("/compare/compare-dataset.sql")
class CompareApiTest {

	@Autowired
	RestTestClient http;

	@Test
	void monzaIn2023And2024() {
		http.get().uri("/api/compare/circuits/monza?years=2023,2024").exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.circuit.ref").isEqualTo("monza")
				.jsonPath("$.years[0].season").isEqualTo(2023)
				.jsonPath("$.years[1].strategySource").isEqualTo("TYRES")
				.jsonPath("$.years[1].strategies[1].stints[0].compound").isEqualTo("MEDIUM")
				.jsonPath("$.years[0].pace[2].medianMs").isEqualTo(99000);
	}

	@Test
	void aYearWithoutARaceThereIsABadRequest() {
		http.get().uri("/api/compare/circuits/monza?years=2023,1990").exchange().expectStatus().isBadRequest()
				.expectBody().jsonPath("$.detail").value(detail -> org.assertj.core.api.Assertions
						.assertThat((String) detail).contains("1990"));
		http.get().uri("/api/compare/circuits/nowhere?years=2023,2024").exchange().expectStatus().isNotFound();
	}

	@Test
	void theYearsToPickFrom() {
		http.get().uri("/api/compare/circuits/monza/seasons").exchange().expectStatus().isOk().expectBody()
				.json("[2023, 2024]");
	}

	@Test
	void verstappenAgainstLeclercIn2024() {
		http.get().uri("/api/compare/drivers?a=max_verstappen&b=leclerc&season=2024").exchange().expectStatus().isOk()
				.expectBody()
				.jsonPath("$.season").isEqualTo(2024)
				.jsonPath("$.tally.aAheadOnGrid").isEqualTo(2)
				.jsonPath("$.races[1].medianGapMs").isEqualTo(1000);
		http.get().uri("/api/compare/drivers?a=leclerc&b=leclerc&season=2024").exchange().expectStatus().isBadRequest();
	}

}

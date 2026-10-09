package com.telemetrix.race;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.circuit.Circuit;
import com.telemetrix.circuit.CircuitRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

// Outer test: the race API over real HTTP, through every layer, against PostgreSQL.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class RaceApiIntegrationTest {

	@Autowired
	RestTestClient http;

	@Autowired
	SeasonRepository seasons;

	@Autowired
	CircuitRepository circuits;

	@Autowired
	RaceRepository races;

	@Autowired
	JdbcTemplate jdbc;

	long bahrainId;

	@BeforeEach
	void seed() {
		jdbc.execute("truncate seasons, circuits, drivers, constructors, races, sessions restart identity cascade");
		var season = seasons.save(new Season((short) 2024));
		seasons.save(new Season((short) 2023));
		var bahrain = circuits.save(new Circuit("bahrain", "Bahrain International Circuit"));
		var jeddah = circuits.save(new Circuit("jeddah", "Jeddah Corniche Circuit"));
		races.save(new Race(season, (short) 2, jeddah, "Saudi Arabian Grand Prix", LocalDate.parse("2024-03-09")));
		var race = new Race(season, (short) 1, bahrain, "Bahrain Grand Prix", LocalDate.parse("2024-03-02"));
		race.setOpenf1MeetingKey(1229);
		race.addSession(new Session("Race", Instant.parse("2024-03-02T15:00:00Z")));
		race.addSession(new Session("Qualifying", Instant.parse("2024-03-01T16:00:00Z")));
		bahrainId = races.save(race).getId();
	}

	@Test
	void listsSeasonsNewestFirst() {
		http.get().uri("/api/seasons").exchange()
				.expectStatus().isOk()
				.expectBody().json("""
						[{"year": 2024, "raceCount": 2}, {"year": 2023, "raceCount": 0}]
						""");
	}

	@Test
	void listsASeasonsRacesInRoundOrder() {
		http.get().uri("/api/seasons/2024/races").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.races[*].name").isEqualTo(java.util.List.of("Bahrain Grand Prix", "Saudi Arabian Grand Prix"))
				.jsonPath("$.races[0].circuit.ref").isEqualTo("bahrain")
				.jsonPath("$.races[0].hasOpenF1Data").isEqualTo(true)
				.jsonPath("$.races[1].hasOpenF1Data").isEqualTo(false);
	}

	@Test
	void showsARaceWithSessionsInStartOrder() {
		http.get().uri("/api/races/{id}", bahrainId).exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.season").isEqualTo(2024)
				.jsonPath("$.openf1MeetingKey").isEqualTo(1229)
				.jsonPath("$.sessions[*].name").isEqualTo(java.util.List.of("Qualifying", "Race"));
	}

	@Test
	void unknownThingsAreProblemDetails() {
		http.get().uri("/api/races/999999").exchange()
				.expectStatus().isNotFound()
				.expectBody().jsonPath("$.detail").isEqualTo("Race 999999 not found");
		http.get().uri("/api/seasons/1999/races").exchange().expectStatus().isNotFound();
	}

	@Test
	void publishesAnOpenApiDocumentAndSwaggerUi() {
		var doc = http.get().uri("/v3/api-docs").exchange()
				.expectStatus().isOk()
				.expectBody(String.class).returnResult().getResponseBody();
		assertThat(doc).contains("/api/seasons", "/api/seasons/{year}/races", "/api/races/{id}");
		http.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
	}

}

package com.telemetrix.ingestion.jolpica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.telemetrix.FakeTime;
import com.telemetrix.ingestion.ExternalApiException;
import com.telemetrix.ingestion.RateLimitedClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// The Jolpica (Ergast-compatible) client against recorded responses.
class JolpicaClientTest {

	static final String BASE = "https://jolpica.test/ergast/f1";

	MockRestServiceServer server;

	FakeTime time;

	JolpicaClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		time = new FakeTime(Instant.parse("2026-01-01T00:00:00Z"));
		client = new JolpicaClient(new RateLimitedClient("Jolpica", builder.build(), 3, Duration.ofSeconds(2),
				Duration.ofSeconds(8), time, time));
	}

	@Test
	void fetchesASeasonsCalendarWithCircuits() {
		server.expect(requestTo(BASE + "/2024/races.json?limit=100")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(fixture("races_2024.json"), MediaType.APPLICATION_JSON));

		var races = client.races(2024);

		server.verify();
		assertThat(races).hasSize(24);
		var bahrain = races.getFirst();
		assertThat(bahrain.season()).isEqualTo(2024);
		assertThat(bahrain.round()).isEqualTo(1);
		assertThat(bahrain.raceName()).isEqualTo("Bahrain Grand Prix");
		assertThat(bahrain.date()).isEqualTo(LocalDate.parse("2024-03-02"));
		var circuit = bahrain.circuit();
		assertThat(circuit.circuitId()).isEqualTo("bahrain");
		assertThat(circuit.circuitName()).isEqualTo("Bahrain International Circuit");
		assertThat(circuit.location().locality()).isEqualTo("Sakhir");
		assertThat(circuit.location().country()).isEqualTo("Bahrain");
		assertThat(circuit.location().lat()).isEqualByComparingTo(new BigDecimal("26.0325"));
		assertThat(circuit.location().lng()).isEqualByComparingTo(new BigDecimal("50.5106"));
		assertThat(races.getLast().round()).isEqualTo(24);
	}

	@Test
	void anUnknownSeasonIsAnEmptyCalendar() {
		server.expect(requestTo(BASE + "/1900/races.json?limit=100")).andRespond(withSuccess("""
				{"MRData": {"total": "0", "RaceTable": {"season": "1900", "Races": []}}}
				""", MediaType.APPLICATION_JSON));

		assertThat(client.races(1900)).isEmpty();
	}

	@Test
	void sharesTheRetryBehaviourAndNamesTheSource() {
		server.expect(times(3), requestTo(BASE + "/2024/races.json?limit=100")).andRespond(withServerError());

		assertThatThrownBy(() -> client.races(2024)).isInstanceOf(ExternalApiException.class)
				.hasMessageContaining("Jolpica GET /2024/races.json?limit=100")
				.hasMessageContaining("3 attempts");
	}

	private static ClassPathResource fixture(String name) {
		return new ClassPathResource("fixtures/jolpica/" + name);
	}

}

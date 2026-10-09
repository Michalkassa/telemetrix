package com.telemetrix.ingestion.multiviewer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.telemetrix.FakeTime;
import com.telemetrix.ingestion.RateLimitedClient;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MultiviewerClientTest {

	static final String BASE = "https://multiviewer.test/api/v1";

	MockRestServiceServer server;

	MultiviewerClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		var time = new FakeTime(Instant.parse("2026-01-01T00:00:00Z"));
		client = new MultiviewerClient(new RateLimitedClient("Multiviewer", builder.build(), 3, Duration.ofSeconds(1),
				Duration.ofSeconds(1), time, time));
	}

	@Test
	void fetchesACircuitsTrackMap() {
		server.expect(requestTo(BASE + "/circuits/63/2024"))
				.andRespond(withSuccess(new ClassPathResource("fixtures/multiviewer/circuit_63_2024.json"),
						MediaType.APPLICATION_JSON));

		var circuit = client.circuit(63, 2024).orElseThrow();

		assertThat(circuit.x()).hasSize(730).startsWith(-375, -372);
		assertThat(circuit.y()).hasSize(730).startsWith(1344, 1443);
		assertThat(circuit.rotation()).isEqualTo(92.0);
		assertThat(circuit.corners()).hasSize(15);
		assertThat(circuit.corners().getFirst().number()).isEqualTo(1);
		assertThat(circuit.corners().getFirst().trackPosition().x()).isCloseTo(42.41, org.assertj.core.api.Assertions.within(0.01));
		assertThat(circuit.miniSectorsIndexes()).hasSize(28).startsWith(20, 38).endsWith(729);
	}

	@Test
	void anUnknownCircuitIsEmpty() {
		server.expect(requestTo(BASE + "/circuits/999/2024")).andRespond(withResourceNotFound());

		assertThat(client.circuit(999, 2024)).isEmpty();
	}

}

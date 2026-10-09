package com.telemetrix;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

// Docker Compose's healthcheck asks /actuator/health whether the backend is ready: UP once the app has
// started and the database answers.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ActuatorHealthTest {

	@Autowired
	RestTestClient http;

	@Test
	void healthIsUpWithTheDatabase() {
		http.get().uri("/actuator/health").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void onlyHealthIsExposed() {
		http.get().uri("/actuator/env").exchange().expectStatus().isNotFound();
	}

}

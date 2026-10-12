package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// A burst of requests hits 429 (the Check-it): anonymous callers are limited by address, signed-in ones
// by who they are, each with its own allowance. Low limits here; the defaults are in SecurityProperties.
@SpringBootTest(properties = { "telemetrix.security.rate-limit.anonymous-per-minute=3",
		"telemetrix.security.rate-limit.authenticated-per-minute=5" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	TokenService tokens;

	@Test
	void aBurstOfAnonymousRequestsIsRefused() {
		for (int i = 0; i < 3; i++) {
			assertThat(mvc.get().uri("/api/seasons").with(from("10.0.0.1"))).hasStatusOk()
					.containsHeader("X-Rate-Limit-Remaining");
		}

		assertThat(mvc.get().uri("/api/seasons").with(from("10.0.0.1"))).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
				.containsHeader("Retry-After")
				.bodyJson().extractingPath("$.status").isEqualTo(429);
		assertThat(mvc.get().uri("/api/seasons").with(from("10.0.0.2"))).hasStatusOk(); // someone else
	}

	@Test
	void aSignedInCallerHasTheirOwnLargerAllowance() {
		String bearer = "Bearer " + tokens.issue("github:busy").accessToken();
		for (int i = 0; i < 5; i++) {
			assertThat(mvc.get().uri("/api/seasons").header("Authorization", bearer).with(from("10.0.0.3")))
					.hasStatusOk();
		}

		assertThat(mvc.get().uri("/api/seasons").header("Authorization", bearer).with(from("10.0.0.3")))
				.hasStatus(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void theHealthCheckIsNotLimited() {
		for (int i = 0; i < 6; i++) {
			assertThat(mvc.get().uri("/actuator/health").with(from("10.0.0.4"))).hasStatusOk();
		}
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String address) {
		return request -> {
			request.setRemoteAddr(address);
			return request;
		};
	}

}

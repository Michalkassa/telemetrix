package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.eventstore.Projections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.mockito.BDDMockito.given;

// Programmatic access with an API key (X-API-Key): each configured key (only its SHA-256 is stored) acts
// with its own role. The keys are in src/test/resources/config/application.properties.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiKeyTest {

	static final String ADMIN_KEY = "test-admin-key-0123456789";

	static final String ANALYST_KEY = "test-analyst-key-0123456789";

	@MockitoBean
	Projections projections;

	@Autowired
	MockMvcTester mvc;

	@Test
	void aKeyActsWithItsRole() {
		given(projections.rebuildAll()).willReturn(List.of("timeline"));

		assertThat(mvc.post().uri("/api/admin/projections/rebuild").header("X-API-Key", ADMIN_KEY)).hasStatusOk();
		assertThat(mvc.post().uri("/api/admin/projections/rebuild").header("X-API-Key", ANALYST_KEY))
				.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void theCallerIsTheKeysName() {
		assertThat(mvc.get().uri("/api/auth/me").header("X-API-Key", ANALYST_KEY)).hasStatusOk().bodyJson()
				.isLenientlyEqualTo("""
						{"subject": "api-key:notebook", "roles": ["ANALYST"]}
						""");
	}

	@Test
	void anUnknownKeyIsRejectedEvenWhereNoneIsNeeded() {
		assertThat(mvc.get().uri("/api/seasons").header("X-API-Key", "guess")).hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.status").isEqualTo(401);
	}

	@Test
	void aKeyIsNotAcceptedAsABearerToken() {
		assertThat(mvc.get().uri("/api/auth/me").header("Authorization", "Bearer " + ADMIN_KEY))
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

}

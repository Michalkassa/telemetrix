package com.telemetrix.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Every error the API returns is an RFC 9457 problem detail. A test-only controller throws each
// kind of exception so the handler can be tested without any real feature.
@WebMvcTest(GlobalExceptionHandlerTest.ThrowingController.class)
// controller behaviour only: who may call it is AuthorizationMatrixTest's, against the real filter chain
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void notFoundExceptionIs404ProblemDetail() {
		assertThat(mvc.get().uri("/test/not-found"))
				.hasStatus(HttpStatus.NOT_FOUND)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"title": "Not Found", "status": 404, "detail": "Race 42 not found",
						 "instance": "/test/not-found"}
						""");
	}

	@Test
	void conflictExceptionIs409ProblemDetail() {
		assertThat(mvc.get().uri("/test/conflict"))
				.hasStatus(HttpStatus.CONFLICT)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"title": "Conflict", "status": 409, "detail": "Session 9158 already imported"}
						""");
	}

	@Test
	void invalidBodyIs400NamingEachFailingField() {
		assertThat(mvc.post().uri("/test/validated")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "", "laps": 0}
						"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"status": 400, "errors": {"name": "must not be blank", "laps": "must be greater than 0"}}
						""");
	}

	@Test
	void unexpectedExceptionIs500WithoutLeakingInternals() {
		assertThat(mvc.get().uri("/test/boom"))
				.hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyText()
				.contains("Unexpected error")
				.doesNotContain("relation \"secret_table\"");
	}

	@RestController
	static class ThrowingController {

		record Body(@NotBlank String name, @Positive int laps) {
		}

		@GetMapping("/test/not-found")
		void notFound() {
			throw new NotFoundException("Race", 42);
		}

		@GetMapping("/test/conflict")
		void conflict() {
			throw new ConflictException("Session 9158 already imported");
		}

		@PostMapping("/test/validated")
		void validated(@Valid @RequestBody Body body) {
		}

		@GetMapping("/test/boom")
		void boom() {
			throw new IllegalStateException("SQL error: relation \"secret_table\" does not exist");
		}

	}

}

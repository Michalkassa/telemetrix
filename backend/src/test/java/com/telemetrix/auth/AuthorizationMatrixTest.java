package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.eventstore.Projections;
import com.telemetrix.ingestion.CalendarImporter;
import com.telemetrix.ingestion.RaceImportCoordinator;
import com.telemetrix.ingestion.RaceImporter;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.history.HistoryImportService;
import com.telemetrix.ingestion.jolpica.JolpicaClient;
import com.telemetrix.ingestion.multiviewer.MultiviewerClient;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// The authorization matrix (docs/adr/0007): who may call what, against the real filter chain. Reading is
// open to everyone, as are opening a race (it downloads on demand) and watching together; the event log
// needs ANALYST, imports and rebuilds ADMIN, and any other write a signed-in caller. "ok" means the
// request got past security (its answer — 200, 404, 400 — is the controller's).
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorizationMatrixTest {

	// nothing here may reach a real data source or start a real import
	@MockitoBean
	OpenF1Client openF1;

	@MockitoBean
	JolpicaClient jolpica;

	@MockitoBean
	MultiviewerClient multiviewer;

	@MockitoBean
	F1ArchiveClient f1Archive;

	@MockitoBean
	CalendarImporter calendarImporter;

	@MockitoBean
	RaceImporter raceImporter;

	@MockitoBean
	RaceImportCoordinator raceImports;

	@MockitoBean
	HistoryImportService historyImport;

	@MockitoBean
	Projections projections;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	TokenService tokens;

	enum Caller {
		ANONYMOUS(null), USER("github:someone"), ANALYST("github:test-analyst"), ADMIN("github:test-admin");

		final String subject;

		Caller(String subject) {
			this.subject = subject;
		}
	}

	static final String OK = "ok";

	static Stream<Arguments> matrix() {
		return Stream.of(
				// method, path, anonymous, USER, ANALYST, ADMIN
				row("GET", "/api/seasons", OK, OK, OK, OK),
				row("GET", "/api/seasons/2024/races", OK, OK, OK, OK),
				row("GET", "/api/races/1", OK, OK, OK, OK),
				row("GET", "/api/races/1/timeline", OK, OK, OK, OK),
				row("GET", "/api/seasons/2024/standings", OK, OK, OK, OK),
				row("GET", "/api/drivers", OK, OK, OK, OK),
				row("GET", "/api/drivers/hamilton/stats", OK, OK, OK, OK),
				row("GET", "/api/compare/drivers?season=2024&a=hamilton&b=russell", OK, OK, OK, OK),
				row("GET", "/api/sessions/1/replay", OK, OK, OK, OK),
				row("GET", "/api/sessions/1/telemetry?driver=1&lap=1", OK, OK, OK, OK),
				row("POST", "/api/races/1/import", OK, OK, OK, OK),
				row("POST", "/api/watch", OK, OK, OK, OK),
				row("GET", "/actuator/health", OK, OK, OK, OK),
				row("GET", "/actuator/env", "404", "404", "404", "404"), // not exposed at all
				row("GET", "/v3/api-docs", OK, OK, OK, OK),
				row("GET", "/api/auth/me", "401", OK, OK, OK),
				row("GET", "/api/sessions/1/events", "401", "403", OK, OK),
				row("GET", "/api/sessions/1/events/1/state", "401", "403", OK, OK),
				row("POST", "/api/admin/import/seasons/2024", "401", "403", "403", OK),
				row("POST", "/api/admin/import/races/1", "401", "403", "403", OK),
				row("POST", "/api/admin/import/history", "401", "403", "403", OK),
				row("GET", "/api/admin/import/history", "401", "403", "403", OK),
				row("POST", "/api/admin/projections/rebuild", "401", "403", "403", OK),
				row("DELETE", "/api/seasons", "401", OK, OK, OK));
	}

	static Arguments row(String method, String path, String anonymous, String user, String analyst, String admin) {
		return Arguments.of(method, path, anonymous, user, analyst, admin);
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("matrix")
	void whoMayCallWhat(String method, String path, String anonymous, String user, String analyst, String admin) {
		expect(Caller.ANONYMOUS, method, path, anonymous);
		expect(Caller.USER, method, path, user);
		expect(Caller.ANALYST, method, path, analyst);
		expect(Caller.ADMIN, method, path, admin);
	}

	private void expect(Caller caller, String method, String path, String expected) {
		var request = mvc.method(HttpMethod.valueOf(method)).uri(path);
		if (method.equals("POST")) {
			request = request.contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\": 1}");
		}
		if (caller.subject != null) {
			request = request.header("Authorization", "Bearer " + tokens.issue(caller.subject).accessToken());
		}
		MvcTestResult result = request.exchange();
		int status = result.getResponse().getStatus();
		if (expected.equals(OK)) {
			assertThat(status).as("%s %s %s", caller, method, path).isNotIn(401, 403);
		}
		else {
			assertThat(status).as("%s %s %s", caller, method, path).isEqualTo(Integer.parseInt(expected));
		}
	}

}

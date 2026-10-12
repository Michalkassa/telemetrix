package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.util.UriUtils;

// Hostile input (docs/adr/0007): SQL in path and query parameters, script in text, is refused with a 400
// before it reaches a query — and never echoed back. Every query is parameterised anyway; this checks the
// input rules on top, and that the data is untouched afterwards.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InjectionTest {

	static final String SQL = "x' OR '1'='1";

	static final String DROP = "x'; drop table races;--";

	static final String SCRIPT = "<script>alert(1)</script>";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void aSeason() {
		jdbc.update("insert into seasons (year) values (2024) on conflict do nothing");
	}

	static Stream<String> hostile() {
		return Stream.of(
				"/api/drivers/{0}/stats", "/api/drivers/{0}/races", "/api/constructors/{0}/stats",
				"/api/circuits/{0}/layout?year=2024", "/api/compare/circuits/{0}?years=2023",
				"/api/compare/circuits/{0}/seasons", "/api/compare/drivers?season=2024&a={0}&b=hamilton",
				"/api/watch/{0}", "/api/seasons/2024/standings?round={0}", "/api/races/1/timeline?type={0}",
				"/api/races/{0}", "/api/sessions/1/replay/state?at={0}")
				.flatMap(path -> Stream.of(SQL, DROP, SCRIPT).map(payload -> path.replace("{0}", encode(payload))));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("hostile")
	void isRefusedWithoutBeingEchoed(String uri) throws Exception {
		var result = mvc.get().uri(java.net.URI.create(uri)).exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result.getResponse().getContentAsString()).doesNotContain("<script", "drop table", "'1'='1");
	}

	@Test
	void theDataIsUntouched() {
		hostile().forEach(uri -> mvc.get().uri(java.net.URI.create(uri)).exchange());

		assertThat(jdbc.queryForObject("select count(*) from seasons where year = 2024", Long.class)).isOne();
		assertThat(jdbc.queryForObject("select to_regclass('races') is not null", Boolean.class)).isTrue();
	}

	@Test
	void scriptInABodyIsRefusedWithoutBeingEchoed() throws Exception {
		var result = mvc.post().uri("/api/watch").contentType(MediaType.APPLICATION_JSON)
				.content("{\"sessionId\": \"" + SCRIPT + "\"}").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result.getResponse().getContentAsString()).doesNotContain("<script");
	}

	@Test
	void securityHeadersStopBrowsersSniffingOrFramingResponses() {
		assertThat(mvc.get().uri("/api/seasons")).hasStatusOk()
				.hasHeader("X-Content-Type-Options", "nosniff")
				.hasHeader("X-Frame-Options", "DENY");
	}

	private static String encode(String value) {
		return UriUtils.encode(value, StandardCharsets.UTF_8);
	}

}

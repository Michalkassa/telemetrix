package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.eventstore.Projections;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.util.UriComponentsBuilder;

// Outer test (docs/adr/0007): signing in with GitHub gets you our own JWTs, which then open what your role
// allows; the refresh token buys the next pair. GitHub is a fake here (its authorize, token and user
// endpoints on a local port); test-admin is on the admin allowlist (src/test/resources/config).
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GitHubLoginFlowTest {

	static final FakeGitHub GITHUB = FakeGitHub.start();

	@DynamicPropertySource
	static void github(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.registration.github.client-id", () -> "test-client");
		registry.add("spring.security.oauth2.client.registration.github.client-secret", () -> "test-secret");
		registry.add("spring.security.oauth2.client.provider.github.authorization-uri", () -> GITHUB.url("/login/oauth/authorize"));
		registry.add("spring.security.oauth2.client.provider.github.token-uri", () -> GITHUB.url("/login/oauth/access_token"));
		registry.add("spring.security.oauth2.client.provider.github.user-info-uri", () -> GITHUB.url("/user"));
		registry.add("telemetrix.security.login-redirect", () -> "http://app.test/auth/callback");
	}

	@AfterAll
	static void stopGitHub() {
		GITHUB.stop();
	}

	@MockitoBean
	Projections projections;

	@Autowired
	MockMvcTester mvc;

	@Test
	void signInWithGitHubThenCallWhatTheRoleAllows() {
		Map<String, String> tokens = signIn("test-admin", 4242);

		assertThat(tokens).containsKeys("access_token", "refresh_token").containsEntry("expires_in", "900");
		assertThat(mvc.get().uri("/api/auth/me").header("Authorization", "Bearer " + tokens.get("access_token")))
				.hasStatusOk().bodyJson().isLenientlyEqualTo("""
						{"subject": "github:test-admin", "roles": ["ADMIN"]}
						""");
		given(projections.rebuildAll()).willReturn(List.of("timeline"));
		assertThat(mvc.post().uri("/api/admin/projections/rebuild")
				.header("Authorization", "Bearer " + tokens.get("access_token"))).hasStatusOk();
		String basic = java.util.Base64.getEncoder().encodeToString("test-client:test-secret".getBytes(StandardCharsets.UTF_8));
		assertThat(GITHUB.tokenRequests()).singleElement().asString().contains("code=the-code")
				.contains("Authorization: Basic " + basic); // the client's credentials (client_secret_basic)
	}

	@Test
	void someoneNotOnAnAllowlistIsAUser() {
		Map<String, String> tokens = signIn("someone", 7);

		assertThat(mvc.post().uri("/api/admin/projections/rebuild")
				.header("Authorization", "Bearer " + tokens.get("access_token"))).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void theRefreshTokenBuysANewPair() {
		Map<String, String> tokens = signIn("someone", 7);

		var refreshed = mvc.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\": \"" + tokens.get("refresh_token") + "\"}").exchange();

		assertThat(refreshed).hasStatusOk().bodyJson().extractingPath("$.expiresIn").isEqualTo(900);
		assertThat(mvc.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\": \"" + tokens.get("access_token") + "\"}")) // not a refresh token
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void aRefusedSignInGoesBackToTheAppWithAnError() {
		var session = new MockHttpSession();
		String state = state(mvc.get().uri("/oauth2/authorization/github").session(session).exchange()
				.getResponse().getRedirectedUrl());

		var back = mvc.get().uri("/login/oauth2/code/github").param("error", "access_denied").param("state", state)
				.session(session).exchange();

		assertThat(back.getResponse().getRedirectedUrl()).isEqualTo("http://app.test/auth/callback#error=login_failed");
	}

	// the browser's round trip: to GitHub, back with a code; the app's answer carries the tokens
	private Map<String, String> signIn(String login, long id) {
		GITHUB.signedInAs(login, id);
		var session = new MockHttpSession();
		var toGitHub = mvc.get().uri("/oauth2/authorization/github").session(session).exchange();
		String authorize = toGitHub.getResponse().getRedirectedUrl();
		assertThat(authorize).startsWith(GITHUB.url("/login/oauth/authorize"));

		var back = mvc.get().uri("/login/oauth2/code/github").param("code", "the-code").param("state", state(authorize))
				.session(session).exchange();

		String redirect = back.getResponse().getRedirectedUrl();
		assertThat(redirect).startsWith("http://app.test/auth/callback#");
		Map<String, String> fragment = new LinkedHashMap<>();
		for (String pair : URI.create(redirect).getRawFragment().split("&")) {
			String[] kv = pair.split("=", 2);
			fragment.put(kv[0], kv[1]);
		}
		return fragment;
	}

	// as the browser would send it back: decoded
	private static String state(String authorizeUrl) {
		String raw = UriComponentsBuilder.fromUriString(authorizeUrl).build().getQueryParams().getFirst("state");
		return URLDecoder.decode(raw, StandardCharsets.UTF_8);
	}

	/** GitHub's OAuth endpoints, faked: any code buys a token; /user is whoever signedInAs says. */
	static final class FakeGitHub {

		private final HttpServer server;

		private final List<String> tokenRequests = new CopyOnWriteArrayList<>();

		private volatile String user = "{}";

		private FakeGitHub(HttpServer server) {
			this.server = server;
		}

		static FakeGitHub start() {
			try {
				var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
				var github = new FakeGitHub(server);
				server.createContext("/login/oauth/access_token", exchange -> {
					github.tokenRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
							+ "\nAuthorization: " + exchange.getRequestHeaders().getFirst("Authorization"));
					json(exchange, "{\"access_token\": \"gho_fake\", \"token_type\": \"bearer\", \"scope\": \"read:user\"}");
				});
				server.createContext("/user", exchange -> json(exchange, github.user));
				server.start();
				return github;
			}
			catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		void signedInAs(String login, long id) {
			tokenRequests.clear();
			user = "{\"id\": " + id + ", \"login\": \"" + login + "\", \"name\": \"Test\"}";
		}

		List<String> tokenRequests() {
			return tokenRequests;
		}

		String url(String path) {
			return "http://127.0.0.1:" + server.getAddress().getPort() + path;
		}

		void stop() {
			server.stop(0);
		}

		private static void json(HttpExchange exchange, String body) throws IOException {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		}

	}

}

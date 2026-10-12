package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemetrix.FakeTime;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

// The app's own tokens (docs/adr/0007): RS256-signed JWTs from our issuer. An access token lasts 15
// minutes; a refresh token 14 days and only buys a new pair, with the role worked out again.
class TokenServiceTest {

	static final KeyPair KEYS = Keys.generateRsa();

	FakeTime clock = new FakeTime(Instant.parse("2026-10-12T20:00:00Z"));

	Roles roles = new Roles(List.of("octo-admin"), List.of("octo-analyst"));

	TokenService tokens = service(KEYS, "telemetrix");

	TokenService service(KeyPair keys, String issuer) {
		return new TokenService(keys, issuer, Duration.ofMinutes(15), Duration.ofDays(14), clock, roles);
	}

	@Test
	void anAccessTokenCarriesTheSubjectAndRole() {
		var jwt = tokens.decodeAccess(tokens.issue("github:octo-analyst").accessToken());

		assertThat(jwt.getSubject()).isEqualTo("github:octo-analyst");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ANALYST");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("telemetrix");
		assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse("2026-10-12T20:15:00Z"));
	}

	@Test
	void sayHowLongTheAccessTokenLasts() {
		assertThat(tokens.issue("github:someone").expiresIn()).isEqualTo(900);
	}

	@Test
	void anExpiredAccessTokenIsRejected() {
		String access = tokens.issue("github:someone").accessToken();

		clock.advance(Duration.ofMinutes(17)); // past the minute of allowed clock skew

		assertThatThrownBy(() -> tokens.decodeAccess(access)).isInstanceOf(JwtException.class);
	}

	@Test
	void aTokenSignedWithAnotherKeyIsRejected() {
		String forged = service(Keys.generateRsa(), "telemetrix").issue("github:octo-admin").accessToken();

		assertThatThrownBy(() -> tokens.decodeAccess(forged)).isInstanceOf(JwtException.class);
	}

	@Test
	void aTokenFromAnotherIssuerIsRejected() {
		String foreign = service(KEYS, "someone-else").issue("github:octo-admin").accessToken();

		assertThatThrownBy(() -> tokens.decodeAccess(foreign)).isInstanceOf(JwtException.class);
	}

	@Test
	void aRefreshTokenIsNotAnAccessToken() {
		String refresh = tokens.issue("github:someone").refreshToken();

		assertThatThrownBy(() -> tokens.decodeAccess(refresh)).isInstanceOf(JwtException.class);
	}

	@Test
	void refreshingGivesANewPairWithTheRoleWorkedOutAgain() {
		var first = tokens.issue("github:octo-later");
		roles = new Roles(List.of("octo-later"), List.of()); // made an admin since
		tokens = service(KEYS, "telemetrix");
		clock.advance(Duration.ofMinutes(20));

		var second = tokens.refresh(first.refreshToken());

		var jwt = tokens.decodeAccess(second.accessToken());
		assertThat(jwt.getSubject()).isEqualTo("github:octo-later");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
		assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
	}

	@Test
	void anAccessTokenCannotRefresh() {
		String access = tokens.issue("github:someone").accessToken();

		assertThatThrownBy(() -> tokens.refresh(access)).isInstanceOf(JwtException.class);
	}

	@Test
	void anExpiredRefreshTokenCannotRefresh() {
		String refresh = tokens.issue("github:someone").refreshToken();

		clock.advance(Duration.ofDays(15));

		assertThatThrownBy(() -> tokens.refresh(refresh)).isInstanceOf(JwtException.class);
	}

	@Test
	void garbageIsRejected() {
		assertThatThrownBy(() -> tokens.decodeAccess("not.a.jwt")).isInstanceOf(JwtException.class);
	}

}

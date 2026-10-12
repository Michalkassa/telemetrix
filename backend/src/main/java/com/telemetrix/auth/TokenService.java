package com.telemetrix.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

// The app's own tokens (docs/adr/0007): JWTs signed RS256 with our key, from our issuer. An access token
// (short) carries the subject and its role and is what the API accepts; a refresh token (long) only buys a
// new pair, the role worked out again so allowlist changes apply. `token_use` keeps the two apart.
public class TokenService {

	static final String ROLES = "roles";

	static final String TOKEN_USE = "token_use";

	private final String issuer;

	private final Duration accessTtl;

	private final Duration refreshTtl;

	private final Clock clock;

	private final Roles roles;

	private final NimbusJwtEncoder encoder;

	private final JwtDecoder accessDecoder;

	private final JwtDecoder refreshDecoder;

	public TokenService(KeyPair keys, String issuer, Duration accessTtl, Duration refreshTtl, Clock clock, Roles roles) {
		this.issuer = issuer;
		this.accessTtl = accessTtl;
		this.refreshTtl = refreshTtl;
		this.clock = clock;
		this.roles = roles;
		RSAKey key = new RSAKey.Builder((RSAPublicKey) keys.getPublic()).privateKey((RSAPrivateKey) keys.getPrivate())
				.build();
		this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
		this.accessDecoder = decoder((RSAPublicKey) keys.getPublic(), "access");
		this.refreshDecoder = decoder((RSAPublicKey) keys.getPublic(), "refresh");
	}

	/** A new pair for a subject ("github:octocat"), with its role. */
	public Tokens issue(String subject) {
		Instant now = clock.instant();
		String access = encode(JwtClaimsSet.builder().issuer(issuer).subject(subject).issuedAt(now)
				.expiresAt(now.plus(accessTtl)).id(UUID.randomUUID().toString())
				.claim(ROLES, List.of(roles.roleOf(subject).name())).claim(TOKEN_USE, "access").build());
		String refresh = encode(JwtClaimsSet.builder().issuer(issuer).subject(subject).issuedAt(now)
				.expiresAt(now.plus(refreshTtl)).id(UUID.randomUUID().toString()).claim(TOKEN_USE, "refresh").build());
		return new Tokens(access, refresh, accessTtl.toSeconds());
	}

	/** A new pair for a valid refresh token's subject; JwtException if it isn't one. */
	public Tokens refresh(String refreshToken) {
		return issue(refreshDecoder.decode(refreshToken).getSubject());
	}

	/** A valid access token, decoded; JwtException otherwise. */
	public Jwt decodeAccess(String token) {
		return accessDecoder.decode(token);
	}

	/** What the API's resource server checks bearer tokens with. */
	public JwtDecoder accessDecoder() {
		return accessDecoder;
	}

	private String encode(JwtClaimsSet claims) {
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
				.getTokenValue();
	}

	private JwtDecoder decoder(RSAPublicKey key, String use) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator(); // a minute of clock skew
		timestamps.setClock(clock);
		OAuth2TokenValidator<Jwt> used = jwt -> use.equals(jwt.getClaimAsString(TOKEN_USE))
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not an " + use + " token", null));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(issuer), used));
		return decoder;
	}

}

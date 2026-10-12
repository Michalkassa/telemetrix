package com.telemetrix.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

// Settings under "telemetrix.security." (docs/adr/0007; all optional, these are the defaults).
@ConfigurationProperties(prefix = "telemetrix.security")
@Validated
public record SecurityProperties(
		@DefaultValue @Valid Jwt jwt,
		// GitHub logins that are ADMIN / ANALYST; anyone else who signs in is a USER
		@DefaultValue List<String> admins,
		@DefaultValue List<String> analysts,
		// keys for programmatic access, sent as X-API-Key
		@DefaultValue @Valid List<ApiKey> apiKeys,
		@DefaultValue @Valid RateLimit rateLimit,
		// where the browser goes after signing in with GitHub, the tokens in the URL's fragment
		@DefaultValue("http://localhost:5173/auth/callback") @NotBlank String loginRedirect) {

	/**
	 * The signing key as PEM files (public X.509, private PKCS#8); without them a new key is made at each
	 * start, so tokens don't survive a restart (fine in development, not in production).
	 */
	public record Jwt(@DefaultValue("telemetrix") @NotBlank String issuer, @DefaultValue("15m") Duration accessTtl,
			@DefaultValue("14d") Duration refreshTtl, Resource publicKey, Resource privateKey) {
	}

	/** `sha256`: the key's SHA-256 in hex (the key itself is never stored); `name` shows who called. */
	public record ApiKey(@NotBlank String name, @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String sha256,
			@NotNull Role role) {
	}

	/** Requests a minute per caller: by address when anonymous, by subject or API key when not. */
	public record RateLimit(@DefaultValue("300") @Min(1) long anonymousPerMinute,
			@DefaultValue("1200") @Min(1) long authenticatedPerMinute) {
	}

}

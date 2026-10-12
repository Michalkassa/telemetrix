package com.telemetrix.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// Programmatic access (docs/adr/0007): a request with X-API-Key acts as that key's configured name and role.
// Only each key's SHA-256 is configured, compared in constant time. A key that matches none is refused
// outright (401), even where no key is needed: a client with a wrong key should hear about it.
final class ApiKeyFilter extends OncePerRequestFilter {

	static final String HEADER = "X-API-Key";

	/** A caller authenticated by an API key: "api-key:<name>". */
	static final class ApiKeyAuthentication extends AbstractAuthenticationToken {

		private final String name;

		ApiKeyAuthentication(String name, Role role) {
			super(List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
			this.name = "api-key:" + name;
			setAuthenticated(true);
		}

		@Override
		public Object getCredentials() {
			return "";
		}

		@Override
		public Object getPrincipal() {
			return name;
		}

		@Override
		public String getName() {
			return name;
		}

	}

	private record Key(byte[] sha256, String name, Role role) {
	}

	private final List<Key> keys;

	ApiKeyFilter(List<SecurityProperties.ApiKey> keys) {
		this.keys = keys.stream().map(k -> new Key(HexFormat.of().parseHex(k.sha256()), k.name(), k.role())).toList();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String presented = request.getHeader(HEADER);
		if (presented == null) {
			chain.doFilter(request, response);
			return;
		}
		Optional<Key> key = match(presented);
		if (key.isEmpty()) {
			ProblemResponses.write(request, response, HttpStatus.UNAUTHORIZED, "Unknown API key");
			return;
		}
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new ApiKeyAuthentication(key.get().name(), key.get().role()));
		SecurityContextHolder.setContext(context);
		try {
			chain.doFilter(request, response);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private Optional<Key> match(String presented) {
		byte[] hash = sha256(presented);
		Key found = null;
		for (Key key : keys) { // every key compared, so timing doesn't tell which one nearly matched
			if (MessageDigest.isEqual(key.sha256(), hash)) {
				found = key;
			}
		}
		return Optional.ofNullable(found);
	}

	private static byte[] sha256(String key) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}

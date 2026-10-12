package com.telemetrix.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// Rate limiting on the API (docs/adr/0007): after authentication, so a signed-in caller (or API key) is
// limited as themselves and an anonymous one by address. Over the limit: 429 with Retry-After.
final class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimiter limiter;

	private final SecurityProperties.RateLimit limits;

	RateLimitFilter(RateLimiter limiter, SecurityProperties.RateLimit limits) {
		this.limiter = limiter;
		this.limits = limits;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		var caller = SecurityContextHolder.getContext().getAuthentication();
		boolean signedIn = caller != null && caller.isAuthenticated() && !(caller instanceof AnonymousAuthenticationToken);
		var decision = signedIn ? limiter.tryRequest(caller.getName(), limits.authenticatedPerMinute())
				: limiter.tryRequest("ip:" + request.getRemoteAddr(), limits.anonymousPerMinute());
		response.setHeader("X-Rate-Limit-Remaining", String.valueOf(decision.remaining()));
		if (!decision.allowed()) {
			response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
			ProblemResponses.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests: slow down");
			return;
		}
		chain.doFilter(request, response);
	}

}

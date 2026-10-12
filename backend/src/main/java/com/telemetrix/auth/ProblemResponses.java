package com.telemetrix.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

// 401, 403 and 429 as the API's other errors: RFC 9457 problem details (docs/api.md, Errors). The bearer
// handlers set WWW-Authenticate first. Details are fixed texts: nothing from the request is echoed.
final class ProblemResponses implements AuthenticationEntryPoint, AccessDeniedHandler {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();

	private final BearerTokenAccessDeniedHandler bearerDenied = new BearerTokenAccessDeniedHandler();

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
			throws IOException {
		bearerEntryPoint.commence(request, response, e);
		write(request, response, HttpStatus.UNAUTHORIZED, "Sign in, or send a valid bearer token or API key");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
			throws IOException {
		bearerDenied.handle(request, response, e);
		write(request, response, HttpStatus.FORBIDDEN, "Your role may not do this");
	}

	static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
			throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setInstance(URI.create(request.getRequestURI()));
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write(JSON.writeValueAsString(problem));
	}

}

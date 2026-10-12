package com.telemetrix.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

	record RefreshRequest(@NotBlank String refreshToken) {
	}

	/** Who's calling: "github:octocat" or "api-key:ci", and the roles that grants. */
	record Me(String subject, List<String> roles) {
	}

	private final TokenService tokens;

	AuthController(TokenService tokens) {
		this.tokens = tokens;
	}

	// a new pair for a refresh token (the role worked out again)
	@PostMapping("/api/auth/refresh")
	Tokens refresh(@Valid @RequestBody RefreshRequest request) {
		return tokens.refresh(request.refreshToken());
	}

	@GetMapping("/api/auth/me")
	Me me(Authentication caller) {
		return new Me(caller.getName(), caller.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.filter(a -> a.startsWith("ROLE_")).map(a -> a.substring("ROLE_".length())).sorted().toList());
	}

	@ExceptionHandler(JwtException.class)
	ProblemDetail invalidToken(JwtException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Not a valid refresh token");
	}

}

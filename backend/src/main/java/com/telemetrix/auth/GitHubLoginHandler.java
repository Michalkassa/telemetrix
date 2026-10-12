package com.telemetrix.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

// The end of "Sign in with GitHub" (docs/adr/0007): GitHub's user becomes our subject "github:<login>",
// and the browser goes back to the app with our tokens in the URL's fragment (never sent to a server, so
// not in any log). The login session only carried the OAuth state; it ends here.
final class GitHubLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

	private final TokenService tokens;

	private final String redirect;

	GitHubLoginHandler(TokenService tokens, String redirect) {
		this.tokens = tokens;
		this.redirect = redirect;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		String login = ((OAuth2User) authentication.getPrincipal()).getAttribute("login");
		Tokens issued = tokens.issue("github:" + login);
		endSession(request);
		response.sendRedirect(redirect + "#access_token=" + issued.accessToken() + "&refresh_token="
				+ issued.refreshToken() + "&token_type=Bearer&expires_in=" + issued.expiresIn());
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		endSession(request);
		response.sendRedirect(redirect + "#error=login_failed");
	}

	private static void endSession(HttpServletRequest request) {
		var session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}

}

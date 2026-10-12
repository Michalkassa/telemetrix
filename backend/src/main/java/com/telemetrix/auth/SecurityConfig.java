package com.telemetrix.auth;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

// Who may call what (docs/adr/0007). The API is stateless: a bearer JWT of ours (TokenService) or an API
// key (ApiKeyFilter) says who's calling; reads are open to everyone, as are opening a race (it downloads on demand) and
// watching together. The event log needs ANALYST, imports and rebuilds ADMIN (ADMIN > ANALYST > USER).
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	// callers the rate limiter remembers at once (a bucket is a few hundred bytes)
	private static final int MAX_LIMITED_CALLERS = 100_000;

	@Bean
	Roles roles(SecurityProperties properties) {
		return new Roles(properties.admins(), properties.analysts());
	}

	@Bean
	TokenService tokenService(SecurityProperties properties, Clock clock, Roles roles) {
		var jwt = properties.jwt();
		return new TokenService(keys(jwt), jwt.issuer(), jwt.accessTtl(), jwt.refreshTtl(), clock, roles);
	}

	@Bean
	RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.withDefaultRolePrefix().role("ADMIN").implies("ANALYST").role("ANALYST")
				.implies("USER").build();
	}

	// "Sign in with GitHub": the only part with a session, which holds the OAuth state between the redirect to
	// GitHub and the way back; the app gets our tokens at the end (GitHubLoginHandler).
	@Bean
	@Order(1)
	SecurityFilterChain gitHubLogin(HttpSecurity http, TokenService tokens, SecurityProperties properties)
			throws Exception {
		var handler = new GitHubLoginHandler(tokens, properties.loginRedirect());
		return http
				.securityMatcher("/oauth2/**", "/login/**")
				.authorizeHttpRequests(a -> a.anyRequest().permitAll())
				.oauth2Login(o -> o.loginPage("/oauth2/authorization/github") // no generated login page
						.successHandler(handler)
						.failureHandler(handler))
				.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain api(HttpSecurity http, TokenService tokens, SecurityProperties properties, Clock clock)
			throws Exception {
		var problems = new ProblemResponses();
		var roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName(TokenService.ROLES);
		roles.setAuthorityPrefix("ROLE_");
		var authentication = new JwtAuthenticationConverter();
		authentication.setJwtGrantedAuthoritiesConverter(roles);
		return http
				.csrf(AbstractHttpConfigurer::disable) // no cookies: a bearer token or API key on every request
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(a -> a
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/sessions/*/events", "/api/sessions/*/events/**").hasRole("ANALYST")
						.requestMatchers("/api/auth/me").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/races/*/import", "/api/watch")
						.permitAll()
						// which actuator endpoints exist is management.endpoints.web.exposure.include's (only
						// health): one exposed later (Step 42) must get its own rule above
						.requestMatchers(HttpMethod.GET, "/api/**", "/actuator/**", "/v3/api-docs", "/v3/api-docs/**",
								"/swagger-ui.html", "/swagger-ui/**")
						.permitAll()
						.requestMatchers("/ws", "/ws/**", "/error").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(o -> o
						.jwt(j -> j.decoder(tokens.accessDecoder()).jwtAuthenticationConverter(authentication))
						.authenticationEntryPoint(problems)
						.accessDeniedHandler(problems))
				.exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
				.addFilterBefore(new ApiKeyFilter(properties.apiKeys()), BearerTokenAuthenticationFilter.class)
				.addFilterBefore(new RateLimitFilter(new RateLimiter(clock, MAX_LIMITED_CALLERS), properties.rateLimit()),
						AuthorizationFilter.class)
				.build();
	}

	static KeyPair keys(SecurityProperties.Jwt jwt) {
		if (jwt.publicKey() == null || jwt.privateKey() == null) {
			log.warn("No telemetrix.security.jwt.public-key/private-key: signing with a key made now. "
					+ "Tokens won't survive a restart; set both in production.");
			return Keys.generateRsa();
		}
		try (InputStream pub = jwt.publicKey().getInputStream(); InputStream priv = jwt.privateKey().getInputStream()) {
			RSAPublicKey publicKey = RsaKeyConverters.x509().convert(pub);
			RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(priv);
			return new KeyPair(publicKey, privateKey);
		}
		catch (IOException e) {
			throw new IllegalStateException("Can't read the JWT signing key", e);
		}
	}

}

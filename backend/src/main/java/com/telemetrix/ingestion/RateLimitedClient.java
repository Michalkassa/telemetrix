package com.telemetrix.ingestion;

import com.telemetrix.common.Sleeper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// A polite GET client for one external API, shared by the source-specific clients:
// - requests are spaced at least minRequestInterval apart (the source's rate limit);
// - 429 and 5xx responses and network errors are retried with exponential backoff, honouring
//   Retry-After; other errors fail at once. Both end in an ExternalApiException;
// - a 404 is "nothing there": an empty Optional.
public class RateLimitedClient {

	private static final Logger log = LoggerFactory.getLogger(RateLimitedClient.class);

	private final String sourceName;

	private final RestClient http;

	private final int maxAttempts;

	private final Duration initialBackoff;

	private final Duration minRequestInterval;

	private final Clock clock;

	private final Sleeper sleeper;

	private Instant nextRequestAllowedAt = Instant.MIN;

	public RateLimitedClient(String sourceName, RestClient http, int maxAttempts, Duration initialBackoff,
			Duration minRequestInterval, Clock clock, Sleeper sleeper) {
		this.sourceName = sourceName;
		this.http = http;
		this.maxAttempts = maxAttempts;
		this.initialBackoff = initialBackoff;
		this.minRequestInterval = minRequestInterval;
		this.clock = clock;
		this.sleeper = sleeper;
	}

	public <T> Optional<T> get(String path, Map<String, ?> query, ParameterizedTypeReference<T> type) {
		String request = describe(path, query);
		for (int attempt = 1;; attempt++) {
			awaitTurn();
			try {
				T body = http.get()
						.uri(uri -> {
							uri.path(path);
							query.forEach(uri::queryParam);
							return uri.build();
						})
						.retrieve()
						.body(type);
				return Optional.ofNullable(body);
			}
			catch (HttpClientErrorException.NotFound e) {
				return Optional.empty();
			}
			catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException | ResourceAccessException e) {
				if (attempt >= maxAttempts) {
					throw new ExternalApiException(sourceName + " GET " + request + " failed after " + attempt
							+ " attempts: " + e.getMessage(), e);
				}
				Duration wait = retryAfter(e).orElse(initialBackoff.multipliedBy(1L << (attempt - 1)));
				log.warn("{} GET {} failed ({}), retrying in {}", sourceName, request, e.getMessage(), wait);
				sleeper.sleep(wait);
			}
			catch (RestClientResponseException e) {
				throw new ExternalApiException(sourceName + " GET " + request + " failed: " + e.getMessage(), e);
			}
		}
	}

	// Rate limit: wait until at least minRequestInterval has passed since the previous request.
	private synchronized void awaitTurn() {
		Instant now = clock.instant();
		if (now.isBefore(nextRequestAllowedAt)) {
			sleeper.sleep(Duration.between(now, nextRequestAllowedAt));
			now = nextRequestAllowedAt;
		}
		nextRequestAllowedAt = now.plus(minRequestInterval);
	}

	// Retry-After in seconds, if the server sent one.
	private static Optional<Duration> retryAfter(Exception e) {
		if (e instanceof RestClientResponseException response && response.getResponseHeaders() != null) {
			String value = response.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
			if (value != null && value.strip().matches("\\d+")) {
				return Optional.of(Duration.ofSeconds(Long.parseLong(value.strip())));
			}
		}
		return Optional.empty();
	}

	private static String describe(String path, Map<String, ?> query) {
		if (query.isEmpty()) {
			return path;
		}
		return path + "?" + query.entrySet().stream()
				.map(e -> e.getKey() + "=" + e.getValue())
				.collect(Collectors.joining("&"));
	}

}

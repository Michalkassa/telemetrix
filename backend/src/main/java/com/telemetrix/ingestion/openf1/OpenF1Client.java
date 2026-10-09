package com.telemetrix.ingestion.openf1;

import com.telemetrix.common.Sleeper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// Talks to the OpenF1 API (https://openf1.org). Owns everything about HOW: URLs, JSON shapes,
// rate limiting and retries. Callers only say WHAT they want and get plain records back.
//
// - Requests are spaced at least minRequestInterval apart (OpenF1's rate limit).
// - 429 and 5xx responses and network errors are retried with exponential backoff, honouring
//   Retry-After; other errors fail at once. Both end in an OpenF1Exception.
// - OpenF1 answers "no matching data" with a 404, which becomes an empty list.
public class OpenF1Client {

	private static final Logger log = LoggerFactory.getLogger(OpenF1Client.class);

	private final RestClient http;

	private final OpenF1Properties props;

	private final Clock clock;

	private final Sleeper sleeper;

	private Instant nextRequestAllowedAt = Instant.MIN;

	public OpenF1Client(RestClient http, OpenF1Properties props, Clock clock, Sleeper sleeper) {
		this.http = http;
		this.props = props;
		this.clock = clock;
		this.sleeper = sleeper;
	}

	/** GET /meetings?year= — every meeting (Grand Prix weekend or test) of a season. */
	public List<OpenF1Meeting> meetings(int year) {
		return getList("/meetings", "year", year, new ParameterizedTypeReference<>() {
		});
	}

	/** GET /sessions?meeting_key= — the sessions of one meeting. */
	public List<OpenF1Session> sessions(int meetingKey) {
		return getList("/sessions", "meeting_key", meetingKey, new ParameterizedTypeReference<>() {
		});
	}

	/** GET /drivers?session_key= — the drivers entered in one session. */
	public List<OpenF1Driver> drivers(int sessionKey) {
		return getList("/drivers", "session_key", sessionKey, new ParameterizedTypeReference<>() {
		});
	}

	private <T> List<T> getList(String path, String param, Object value, ParameterizedTypeReference<List<T>> type) {
		String request = path + "?" + param + "=" + value;
		for (int attempt = 1;; attempt++) {
			awaitTurn();
			try {
				List<T> body = http.get()
						.uri(uri -> uri.path(path).queryParam(param, value).build())
						.retrieve()
						.body(type);
				return body == null ? List.of() : body;
			}
			catch (HttpClientErrorException.NotFound e) {
				return List.of();
			}
			catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException | ResourceAccessException e) {
				if (attempt >= props.maxAttempts()) {
					throw new OpenF1Exception("OpenF1 GET " + request + " failed after " + attempt + " attempts: "
							+ e.getMessage(), e);
				}
				Duration wait = retryAfter(e).orElse(props.initialBackoff().multipliedBy(1L << (attempt - 1)));
				log.warn("OpenF1 GET {} failed ({}), retrying in {}", request, e.getMessage(), wait);
				sleeper.sleep(wait);
			}
			catch (RestClientResponseException e) {
				throw new OpenF1Exception("OpenF1 GET " + request + " failed: " + e.getMessage(), e);
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
		nextRequestAllowedAt = now.plus(props.minRequestInterval());
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

}

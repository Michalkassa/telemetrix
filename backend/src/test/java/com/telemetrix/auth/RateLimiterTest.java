package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.FakeTime;
import com.telemetrix.TestcontainersConfiguration;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

// A token bucket per caller (Bucket4j) kept in Redis (docs/adr/0008): N requests a minute, refilled
// steadily, on the clock we inject; shared by every instance, and forgotten once idle.
class RateLimiterTest {

	static final GenericContainer<?> REDIS = new GenericContainer<>(TestcontainersConfiguration.REDIS).withExposedPorts(6379);

	static RedisClient client;

	FakeTime clock = new FakeTime(Instant.parse("2026-10-12T20:00:00Z"));

	RateLimiter limiter = new RateLimiter(RateLimiter.redisBuckets(client, clock));

	// Redis outlives each test: callers of their own
	String caller = "ip:1.2.3.4/" + UUID.randomUUID();

	@BeforeAll
	static void start() {
		REDIS.start();
		client = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
	}

	@AfterAll
	static void stop() {
		client.shutdown();
		REDIS.stop();
	}

	@Test
	void allowsTheLimitThenRefusesWithHowLongToWait() {
		for (int i = 0; i < 3; i++) {
			assertThat(limiter.tryRequest(caller, 3).allowed()).isTrue();
		}

		var refused = limiter.tryRequest(caller, 3);

		assertThat(refused.allowed()).isFalse();
		assertThat(refused.retryAfterSeconds()).isEqualTo(20); // one request back every 20 s
	}

	@Test
	void saysHowManyAreLeft() {
		assertThat(limiter.tryRequest(caller, 3).remaining()).isEqualTo(2);
	}

	@Test
	void refillsAsTimePasses() {
		for (int i = 0; i < 3; i++) {
			limiter.tryRequest(caller, 3);
		}

		clock.advance(Duration.ofSeconds(20));

		assertThat(limiter.tryRequest(caller, 3).allowed()).isTrue();
		assertThat(limiter.tryRequest(caller, 3).allowed()).isFalse();
	}

	@Test
	void eachCallerHasItsOwnBucket() {
		for (int i = 0; i < 3; i++) {
			limiter.tryRequest(caller, 3);
		}

		assertThat(limiter.tryRequest(caller + "/other", 3).allowed()).isTrue();
	}

	@Test
	void everyInstanceSharesTheBuckets() {
		var otherInstance = new RateLimiter(RateLimiter.redisBuckets(client, clock));
		for (int i = 0; i < 3; i++) {
			limiter.tryRequest(caller, 3);
		}

		assertThat(otherInstance.tryRequest(caller, 3).allowed()).isFalse();
	}

	@Test
	void anIdleBucketIsForgotten() {
		limiter.tryRequest(caller, 3);

		try (var redis = client.connect()) {
			long ttl = redis.sync().pttl("rate:" + caller);
			assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(2).toMillis());
		}
	}

}

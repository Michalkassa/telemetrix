package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.FakeTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// A token bucket per caller (Bucket4j): N requests a minute, refilled steadily; on the clock we inject.
class RateLimiterTest {

	FakeTime clock = new FakeTime(Instant.parse("2026-10-12T20:00:00Z"));

	RateLimiter limiter = new RateLimiter(clock, 1000);

	@Test
	void allowsTheLimitThenRefusesWithHowLongToWait() {
		for (int i = 0; i < 3; i++) {
			assertThat(limiter.tryRequest("ip:1.2.3.4", 3).allowed()).isTrue();
		}

		var refused = limiter.tryRequest("ip:1.2.3.4", 3);

		assertThat(refused.allowed()).isFalse();
		assertThat(refused.retryAfterSeconds()).isEqualTo(20); // one request back every 20 s
	}

	@Test
	void saysHowManyAreLeft() {
		assertThat(limiter.tryRequest("ip:1.2.3.4", 3).remaining()).isEqualTo(2);
	}

	@Test
	void refillsAsTimePasses() {
		for (int i = 0; i < 3; i++) {
			limiter.tryRequest("ip:1.2.3.4", 3);
		}

		clock.advance(Duration.ofSeconds(20));

		assertThat(limiter.tryRequest("ip:1.2.3.4", 3).allowed()).isTrue();
		assertThat(limiter.tryRequest("ip:1.2.3.4", 3).allowed()).isFalse();
	}

	@Test
	void eachCallerHasItsOwnBucket() {
		for (int i = 0; i < 3; i++) {
			limiter.tryRequest("ip:1.2.3.4", 3);
		}

		assertThat(limiter.tryRequest("ip:5.6.7.8", 3).allowed()).isTrue();
		assertThat(limiter.tryRequest("github:someone", 3).allowed()).isTrue();
	}

	@Test
	void forgetsTheLeastRecentCallersBeyondItsSize() {
		var small = new RateLimiter(clock, 2);
		for (int i = 0; i < 3; i++) {
			small.tryRequest("ip:1.2.3.4", 3);
		}
		small.tryRequest("ip:5.6.7.8", 3);
		small.tryRequest("ip:9.9.9.9", 3); // 1.2.3.4 is forgotten: a full bucket again

		assertThat(small.tryRequest("ip:1.2.3.4", 3).allowed()).isTrue();
	}

}

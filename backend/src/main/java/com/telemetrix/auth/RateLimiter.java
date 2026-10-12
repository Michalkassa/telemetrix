package com.telemetrix.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Clock;
import java.time.Duration;

// A token bucket per caller (Bucket4j) kept in Redis (docs/adr/0008), so every instance enforces the
// same limit and a restart doesn't hand out fresh allowances: `perMinute` requests, refilled steadily
// through the minute. Time is the injected clock. A bucket is dropped from Redis a minute after it would
// be full again, so idle callers cost nothing.
final class RateLimiter {

	/** `retryAfterSeconds`: when refused, how long until the next request would be allowed. */
	record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
	}

	private final ProxyManager<String> buckets;

	RateLimiter(ProxyManager<String> buckets) {
		this.buckets = buckets;
	}

	/** Buckets in the Redis that `client` connects to ("rate:<caller>"), on `clock`'s time. */
	static ProxyManager<String> redisBuckets(RedisClient client, Clock clock) {
		var connection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
		return Bucket4jLettuce.casBasedBuilder(connection)
				.clientClock(new TimeMeter() {

					@Override
					public long currentTimeNanos() {
						var now = clock.instant();
						return now.getEpochSecond() * 1_000_000_000L + now.getNano();
					}

					@Override
					public boolean isWallClockBased() {
						return true;
					}

				})
				.expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
				.build();
	}

	Decision tryRequest(String caller, long perMinute) {
		ConsumptionProbe probe = buckets.getProxy("rate:" + caller, () -> BucketConfiguration.builder()
				.addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
				.build())
				.tryConsumeAndReturnRemaining(1);
		long wait = Duration.ofNanos(probe.getNanosToWaitForRefill()).toMillis();
		return new Decision(probe.isConsumed(), probe.getRemainingTokens(), probe.isConsumed() ? 0 : (wait + 999) / 1000);
	}

}

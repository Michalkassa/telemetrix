package com.telemetrix.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

// A token bucket per caller (Bucket4j, in memory; Step 38 can move them to Redis for several instances):
// `perMinute` requests, refilled steadily through the minute. Time is the injected clock. Remembers at most
// `maxCallers` callers, forgetting the least recent (a forgotten caller starts with a full bucket).
final class RateLimiter {

	/** `retryAfterSeconds`: when refused, how long until the next request would be allowed. */
	record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
	}

	private final TimeMeter time;

	private final Map<String, Bucket> buckets;

	RateLimiter(Clock clock, int maxCallers) {
		this.time = new TimeMeter() {

			@Override
			public long currentTimeNanos() {
				var now = clock.instant();
				return now.getEpochSecond() * 1_000_000_000L + now.getNano();
			}

			@Override
			public boolean isWallClockBased() {
				return true;
			}

		};
		this.buckets = new LinkedHashMap<>(16, 0.75f, true) {

			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
				return size() > maxCallers;
			}

		};
	}

	Decision tryRequest(String caller, long perMinute) {
		Bucket bucket;
		synchronized (buckets) {
			bucket = buckets.computeIfAbsent(caller, c -> Bucket.builder()
					.addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
					.withCustomTimePrecision(time)
					.build());
		}
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		long wait = Duration.ofNanos(probe.getNanosToWaitForRefill()).toMillis();
		return new Decision(probe.isConsumed(), probe.getRemainingTokens(), probe.isConsumed() ? 0 : (wait + 999) / 1000);
	}

}

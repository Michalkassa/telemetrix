package com.telemetrix.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The app's sense of time. Inject Clock and Sleeper instead of calling Instant.now() or
// Thread.sleep(), so tests can control time.
@Configuration(proxyBeanMethods = false)
class TimeConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	Sleeper sleeper() {
		return duration -> {
			try {
				Thread.sleep(duration);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting " + duration, e);
			}
		};
	}

}

package com.telemetrix.common;

import java.time.Duration;

// Waiting, as a dependency: production sleeps for real, tests record the wait and move on.
@FunctionalInterface
public interface Sleeper {

	void sleep(Duration duration);

}

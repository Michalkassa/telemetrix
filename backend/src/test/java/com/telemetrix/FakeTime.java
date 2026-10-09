package com.telemetrix;

import com.telemetrix.common.Sleeper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

// Time for tests: a clock that only moves when code "sleeps", so waits are instant and recorded.
public class FakeTime extends Clock implements Sleeper {

	private Instant now;

	private final List<Duration> sleeps = new ArrayList<>();

	public FakeTime(Instant start) {
		this.now = start;
	}

	@Override
	public void sleep(Duration duration) {
		sleeps.add(duration);
		now = now.plus(duration);
	}

	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	public List<Duration> sleeps() {
		return sleeps;
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("FakeTime is always UTC");
	}

}

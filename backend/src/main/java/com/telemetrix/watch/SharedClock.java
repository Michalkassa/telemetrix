package com.telemetrix.watch;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// The server-held playback clock of one watch session (docs/adr/0005): the single source of time that
// everyone watching follows. Race time is anchored — `anchorMs` at `anchorAt` — and moves on with the
// injected Clock while playing; each change re-anchors it and is numbered. Thread-safe.
class SharedClock {

	static final double MIN_SPEED = 0.25;

	static final double MAX_SPEED = 8;

	private final String watchId;

	private final long sessionId;

	private final Clock clock;

	private boolean playing;

	private double speed = 1;

	private long anchorMs;

	private Instant anchorAt;

	private long seq;

	SharedClock(String watchId, long sessionId, Clock clock) {
		this.watchId = watchId;
		this.sessionId = sessionId;
		this.clock = clock;
		this.anchorAt = clock.instant();
	}

	synchronized ClockState state() {
		return new ClockState(watchId, sessionId, seq, playing, speed, anchorMs, anchorAt, clock.instant());
	}

	/** Race time now. */
	synchronized long positionMs() {
		return positionAt(clock.instant());
	}

	synchronized ClockState play() {
		reanchor();
		playing = true;
		return changed();
	}

	synchronized ClockState pause() {
		reanchor();
		playing = false;
		return changed();
	}

	synchronized ClockState seek(long positionMs) {
		anchorMs = Math.max(0, positionMs);
		anchorAt = clock.instant();
		return changed();
	}

	synchronized ClockState speed(double newSpeed) {
		if (!(newSpeed >= MIN_SPEED && newSpeed <= MAX_SPEED)) {
			throw new IllegalArgumentException("Speed must be " + MIN_SPEED + "–" + MAX_SPEED + "×, not " + newSpeed);
		}
		reanchor();
		speed = newSpeed;
		return changed();
	}

	private long positionAt(Instant now) {
		if (!playing) {
			return anchorMs;
		}
		return anchorMs + Math.round(Duration.between(anchorAt, now).toMillis() * speed);
	}

	private void reanchor() {
		Instant now = clock.instant();
		anchorMs = positionAt(now);
		anchorAt = now;
	}

	private ClockState changed() {
		seq++;
		return state();
	}

}

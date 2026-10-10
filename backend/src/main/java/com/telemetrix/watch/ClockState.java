package com.telemetrix.watch;

import java.time.Instant;

/**
 * A watch session's playback clock, as broadcast to everyone watching: race time `positionMs` (after the
 * replay's clockStart) at server time `at`, moving at `speed` while `playing`. So the race time now is
 * positionMs + (now − at) × speed. `serverTime` is when this was sent, for clients to work out their own
 * clock's offset; `seq` numbers the changes in order.
 */
public record ClockState(String watchId, long sessionId, long seq, boolean playing, double speed, long positionMs,
		Instant at, Instant serverTime) {
}

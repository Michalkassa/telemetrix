package com.telemetrix.watch;

import com.telemetrix.common.NotFoundException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

// Watch sessions: one shared clock each, kept in memory (docs/adr/0005). Whoever creates a session gets
// its host key; only commands carrying it move the clock. Anything else — a wrong key, an unknown action,
// a bad value, an unknown session — is ignored.
@Service
class WatchService {

	/** From the host: PLAY, PAUSE, SEEK (value: race time in ms) or SPEED (value: 0.25–8). */
	record Command(String hostKey, String action, Double value) {
	}

	record Created(String watchId, String hostKey, long sessionId) {
	}

	private record Watch(String hostKey, SharedClock clock) {
	}

	private static final SecureRandom RANDOM = new SecureRandom();

	private final Clock clock;

	private final Map<String, Watch> watches = new ConcurrentHashMap<>();

	WatchService(Clock clock) {
		this.clock = clock;
	}

	Created create(long sessionId) {
		String id;
		do {
			id = token(6); // 8 characters, in a link
		}
		while (watches.containsKey(id));
		String hostKey = token(18);
		watches.put(id, new Watch(hostKey, new SharedClock(id, sessionId, clock)));
		return new Created(id, hostKey, sessionId);
	}

	ClockState state(String watchId) {
		return watch(watchId).map(w -> w.clock().state()).orElseThrow(() -> new NotFoundException("Watch session", watchId));
	}

	Optional<ClockState> command(String watchId, Command command) {
		return watch(watchId)
				.filter(w -> command != null && w.hostKey().equals(command.hostKey()))
				.flatMap(w -> apply(w.clock(), command));
	}

	private static Optional<ClockState> apply(SharedClock clock, Command command) {
		try {
			return Optional.ofNullable(switch (String.valueOf(command.action())) {
				case "PLAY" -> clock.play();
				case "PAUSE" -> clock.pause();
				case "SEEK" -> command.value() == null ? null : clock.seek(Math.round(command.value()));
				case "SPEED" -> command.value() == null ? null : clock.speed(command.value());
				default -> null;
			});
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private Optional<Watch> watch(String watchId) {
		return Optional.ofNullable(watchId).map(watches::get);
	}

	private static String token(int bytes) {
		byte[] random = new byte[bytes];
		RANDOM.nextBytes(random);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
	}

}

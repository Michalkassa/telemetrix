package com.telemetrix.watch;

import com.telemetrix.common.NotFoundException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;

// Watch sessions: one shared clock each (docs/adr/0005), kept in a WatchStore — Redis, so any instance
// can serve a session and it survives restarts (docs/adr/0008). Whoever creates a session gets its host
// key; only commands carrying it move the clock, and each change is announced (WatchUpdates) for every
// instance to pass on to its viewers. Anything else — a wrong key, an unknown action, a bad value, an
// unknown session — is ignored. Commands come from the one host, so they don't race each other.
@Service
class WatchService {

	/** From the host: PLAY, PAUSE, SEEK (value: race time in ms) or SPEED (value: 0.25–8). */
	record Command(String hostKey, String action, Double value) {
	}

	record Created(String watchId, String hostKey, long sessionId) {
	}

	private static final SecureRandom RANDOM = new SecureRandom();

	private final WatchStore store;

	private final WatchUpdates updates;

	private final Clock clock;

	WatchService(WatchStore store, WatchUpdates updates, Clock clock) {
		this.store = store;
		this.updates = updates;
		this.clock = clock;
	}

	Created create(long sessionId) {
		String hostKey = token(18);
		String id;
		do {
			id = token(6); // 8 characters, in a link
		}
		while (!store.createIfAbsent(id, new WatchStore.Watch(hostKey, new SharedClock(id, sessionId, clock).saved())));
		return new Created(id, hostKey, sessionId);
	}

	ClockState state(String watchId) {
		return watch(watchId).map(w -> new SharedClock(w.clock(), clock).state())
				.orElseThrow(() -> new NotFoundException("Watch session", watchId));
	}

	Optional<ClockState> command(String watchId, Command command) {
		var watch = watch(watchId).filter(w -> command != null && w.hostKey().equals(command.hostKey()));
		if (watch.isEmpty()) {
			return Optional.empty();
		}
		SharedClock shared = new SharedClock(watch.get().clock(), clock);
		Optional<ClockState> changed = apply(shared, command);
		changed.ifPresent(state -> {
			store.save(watchId, new WatchStore.Watch(watch.get().hostKey(), shared.saved()));
			updates.announce(state);
		});
		return changed;
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

	private Optional<WatchStore.Watch> watch(String watchId) {
		return Optional.ofNullable(watchId).flatMap(store::find);
	}

	private static String token(int bytes) {
		byte[] random = new byte[bytes];
		RANDOM.nextBytes(random);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
	}

}

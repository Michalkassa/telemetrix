package com.telemetrix.telemetry;

import com.telemetrix.cache.Caches;
import com.telemetrix.common.NotFoundException;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.race.Session;
import com.telemetrix.race.SessionRepository;
import com.telemetrix.timing.Lap;
import com.telemetrix.timing.LapRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

// One car's telemetry in one session, downloaded from OpenF1 the first time it is asked for (2
// requests) and served from the database after that (docs/adr/0003).
@Service
public class TelemetryService {

	// Cars run to the grid before the start and back after the flag: accept samples an hour either side.
	static final Duration SESSION_MARGIN = Duration.ofHours(1);

	// a session without an end time (still to finish) is assumed to last at most this long
	static final Duration MAX_SESSION = Duration.ofHours(4);

	// a lap without a next lap or a lap time (the last one of a car) is shown for at most this long
	static final Duration MAX_LAP = Duration.ofMinutes(3);

	private final SessionRepository sessions;

	private final LapRepository laps;

	private final OpenF1Client openF1;

	private final TelemetryStore store;

	private final Clock clock;

	private final ConcurrentHashMap<String, Object> downloadLocks = new ConcurrentHashMap<>();

	TelemetryService(SessionRepository sessions, LapRepository laps, OpenF1Client openF1, TelemetryStore store,
			Clock clock) {
		this.sessions = sessions;
		this.laps = laps;
		this.openF1 = openF1;
		this.store = store;
		this.clock = clock;
	}

	/**
	 * One lap of one car, or (lap null) the whole session. A lap is cached in Redis (docs/adr/0008): the
	 * hot telemetry; a whole session is too big to.
	 */
	@Cacheable(cacheNames = Caches.TELEMETRY_LAPS, key = "#sessionId + ':' + #driver + ':' + #lap",
			condition = "#lap != null")
	public TelemetryResponse telemetry(long sessionId, int driver, Integer lap) {
		Session session = sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("Session", sessionId));
		if (session.getOpenf1SessionKey() == null) {
			throw new NotFoundException("OpenF1 data for session", sessionId);
		}
		ensureDownloaded(session, driver);

		Instant from;
		Instant to;
		if (lap == null) {
			from = windowStart(session);
			to = windowEnd(session);
		}
		else {
			Lap theLap = lap(session, driver, lap);
			from = theLap.getStartsAt();
			to = laps.findBySessionAndDriverNumberAndLapNumber(session, (short) driver, (short) (lap + 1))
					.map(Lap::getStartsAt)
					.orElseGet(() -> theLap.getLapMs() != null ? from.plusMillis(theLap.getLapMs()) : from.plus(MAX_LAP));
		}
		var points = store.points(sessionId, driver, from, to).stream()
				.map(p -> new TelemetryResponse.Point(p.recordedAt(), p.speed(), p.throttle(), p.brake(), p.gear(),
						p.rpm(), p.drs(), p.x(), p.y(), p.z()))
				.toList();
		return new TelemetryResponse(sessionId, driver, lap, from, to, points, store.gaps(sessionId, driver, from, to));
	}

	private Lap lap(Session session, int driver, int lap) {
		return laps.findBySessionAndDriverNumberAndLapNumber(session, (short) driver, (short) lap)
				.filter(l -> l.getStartsAt() != null)
				.orElseThrow(() -> new NotFoundException("Lap " + lap + " of car", driver));
	}

	// Single flight per car and session: a second request waits for the first download instead of
	// starting another one. The download is committed before the lock is released.
	private void ensureDownloaded(Session session, int driver) {
		long sessionId = session.getId();
		if (store.isDownloaded(sessionId, driver)) {
			return;
		}
		Object lock = downloadLocks.computeIfAbsent(sessionId + "/" + driver, key -> new Object());
		synchronized (lock) {
			if (store.isDownloaded(sessionId, driver)) {
				return;
			}
			int key = session.getOpenf1SessionKey();
			var merged = TelemetryMerger.merge(openF1.carData(key, driver), openF1.locations(key, driver),
					windowStart(session), windowEnd(session));
			store.save(sessionId, driver, merged, clock.instant());
		}
	}

	private static Instant windowStart(Session session) {
		return session.getStartsAt().minus(SESSION_MARGIN);
	}

	private static Instant windowEnd(Session session) {
		Instant end = session.getEndsAt() != null ? session.getEndsAt() : session.getStartsAt().plus(MAX_SESSION);
		return end.plus(SESSION_MARGIN);
	}

}

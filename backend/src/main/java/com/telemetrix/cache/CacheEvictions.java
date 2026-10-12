package com.telemetrix.cache;

import com.telemetrix.eventstore.Projection;
import com.telemetrix.eventstore.StreamAppended;
import com.telemetrix.eventstore.StreamType;
import com.telemetrix.ingestion.RaceImported;
import com.telemetrix.ingestion.history.HistoryImported;
import java.util.List;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// What each change makes stale (docs/adr/0008), evicted once the change has committed (an eviction before
// then could be refilled with the old data):
// - a race imported: its sessions' replays, snapshots and timelines, and lap telemetry;
// - a session's stream appended to: its timeline and snapshots;
// - a race's results appended to, or the history imported: the standings.
// Rebuilding the read models (a Projection like them) empties their caches.
@Component
class CacheEvictions implements Projection {

	private static final List<String> READ_MODELS = List.of(Caches.TIMELINES, Caches.SNAPSHOTS, Caches.STANDINGS);

	private final CacheManager caches;

	CacheEvictions(CacheManager caches) {
		this.caches = caches;
	}

	// before viewers are told to fetch again (TimelineUpdates, Order 10)
	@TransactionalEventListener(fallbackExecution = true)
	@Order(0)
	void onRaceImported(RaceImported imported) {
		for (long session : imported.sessionIds()) {
			cache(Caches.REPLAYS).evict(session);
			cache(Caches.SNAPSHOTS).evict(session);
			cache(Caches.TIMELINES).evict(session);
		}
		cache(Caches.TELEMETRY_LAPS).clear(); // keyed session:driver:lap; imports are rare
	}

	@TransactionalEventListener(fallbackExecution = true)
	void onStreamAppended(StreamAppended appended) {
		if (appended.stream().type() == StreamType.SESSION) {
			cache(Caches.TIMELINES).evict(appended.stream().id());
			cache(Caches.SNAPSHOTS).evict(appended.stream().id());
		}
		else if (appended.stream().type() == StreamType.RACE_RESULTS) {
			cache(Caches.STANDINGS).clear(); // a race's results move every later round's standings
		}
	}

	@TransactionalEventListener(fallbackExecution = true)
	void onHistoryImported(HistoryImported imported) {
		cache(Caches.STANDINGS).clear();
	}

	@Override
	public String name() {
		return "caches";
	}

	@Override
	public void rebuild() {
		READ_MODELS.forEach(name -> cache(name).clear());
	}

	private Cache cache(String name) {
		return caches.getCache(name);
	}

}

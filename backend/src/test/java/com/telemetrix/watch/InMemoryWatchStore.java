package com.telemetrix.watch;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// WatchStore for unit tests, without Redis.
class InMemoryWatchStore implements WatchStore {

	private final Map<String, Watch> watches = new ConcurrentHashMap<>();

	@Override
	public boolean createIfAbsent(String watchId, Watch watch) {
		return watches.putIfAbsent(watchId, watch) == null;
	}

	@Override
	public Optional<Watch> find(String watchId) {
		return Optional.ofNullable(watches.get(watchId));
	}

	@Override
	public void save(String watchId, Watch watch) {
		watches.put(watchId, watch);
	}

}

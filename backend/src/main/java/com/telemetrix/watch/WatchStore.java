package com.telemetrix.watch;

import java.util.Optional;

interface WatchStore {

	/** A watch session as kept: its host key and its clock. */
	record Watch(String hostKey, SharedClock.Saved clock) {
	}

	boolean createIfAbsent(String watchId, Watch watch);

	Optional<Watch> find(String watchId);

	void save(String watchId, Watch watch);

}

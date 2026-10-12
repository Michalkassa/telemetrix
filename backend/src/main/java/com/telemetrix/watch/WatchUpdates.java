package com.telemetrix.watch;

@FunctionalInterface
interface WatchUpdates {

	void announce(ClockState state);

}

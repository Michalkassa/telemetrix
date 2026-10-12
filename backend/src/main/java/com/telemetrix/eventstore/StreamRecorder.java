package com.telemetrix.eventstore;

// The write side of one stream type: works out a stream's events from the imported data and appends them.
// The store asks it for a stream that has never been recorded.
public interface StreamRecorder {

	StreamType type();

	void record(long id, EventStore store);

}

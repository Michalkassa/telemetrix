package com.telemetrix.eventstore;

// What a stream is about: SESSION — one race or sprint session's events (its id is the session's);
// RACE_RESULTS — one race weekend's classified results (its id is the race's).
public enum StreamType {
	SESSION, RACE_RESULTS
}

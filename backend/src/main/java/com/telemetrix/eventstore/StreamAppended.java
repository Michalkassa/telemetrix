package com.telemetrix.eventstore;

/** Published when a stream gets a new generation: what was worked out from it is out of date. */
public record StreamAppended(StreamId stream, int generation) {
}

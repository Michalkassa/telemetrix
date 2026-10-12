package com.telemetrix.eventstore;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * An event as stored: `seq` numbers it within its stream's `generation` (from 1), `position` across the
 * whole store; `occurredAt` is null for an event that isn't at a moment.
 */
public record StoredEvent(long position, StreamId stream, int generation, int seq, String type, Instant occurredAt,
		JsonNode payload, Instant recordedAt) {
}

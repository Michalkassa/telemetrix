package com.telemetrix.eventstore;

import java.time.Instant;

/** An event to append: its type, when it happened (null: not at a moment), and a payload Jackson can write. */
public record NewEvent(String type, Instant occurredAt, Object payload) {
}

package com.telemetrix.state;

import java.time.Instant;

/** The derived state after everything that happened up to and including `at`. */
public record Snapshot(Instant at, SnapshotReason reason, int lap, DerivedState state) {
}

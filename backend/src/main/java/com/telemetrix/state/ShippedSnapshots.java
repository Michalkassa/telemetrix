package com.telemetrix.state;

import java.time.Instant;
import java.util.List;

/** The snapshots the browser gets with a race (RaceStateService.shippedSnapshots), with the clock's zero. */
public record ShippedSnapshots(Instant clockStart, List<Snapshot> snapshots) {
}

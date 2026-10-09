package com.telemetrix.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A race session's events in time order; `clockStart` is the replay clock's zero; `codes` car -> "VER". */
public record Timeline(long sessionId, Instant clockStart, Map<Integer, String> codes, List<RaceEvent> events) {
}

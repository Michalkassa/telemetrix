package com.telemetrix.event;

// Published for every event a race session's detectors find (the Observer pattern): other modules listen
// with @EventListener instead of asking.
public record RaceEventDetected(long sessionId, RaceEvent event) {
}

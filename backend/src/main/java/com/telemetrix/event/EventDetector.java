package com.telemetrix.event;

import java.util.List;

// Finds one kind of event in a race. Every detector is a Spring bean; TimelineService runs them all.
@FunctionalInterface
public interface EventDetector {

	List<RaceEvent> detect(RaceData race);

}

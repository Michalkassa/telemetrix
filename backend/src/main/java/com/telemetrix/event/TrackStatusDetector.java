package com.telemetrix.event;

import com.telemetrix.racecontrol.TrackStatus;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// Safety car, VSC, red and yellow flags, from the stored track-status periods (Step 21): one event per
// period, lasting until it ended. A VSC and its ending phase are one VSC.
@Component
class TrackStatusDetector implements EventDetector {

	@Override
	public List<RaceEvent> detect(RaceData race) {
		List<RaceEvent> events = new ArrayList<>();
		List<TrackStatusPeriod> periods = race.trackStatus();
		for (int i = 0; i < periods.size(); i++) {
			TrackStatusPeriod period = periods.get(i);
			TrackStatusPeriod last = period;
			if (period.status() == TrackStatus.VSC && i + 1 < periods.size()
					&& periods.get(i + 1).status() == TrackStatus.VSC_ENDING
					&& periods.get(i + 1).from().equals(period.to())) {
				last = periods.get(++i);
			}
			var to = last.to();
			events.add(new RaceEvent(type(period.status()), period.from(), race.lapAt(period.from()), to,
					to == null ? null : race.lapAt(to), severity(period.status()), List.of(), summary(period.status()),
					null));
		}
		return events;
	}

	private static EventType type(TrackStatus status) {
		return switch (status) {
			case SAFETY_CAR -> EventType.SAFETY_CAR;
			case VSC, VSC_ENDING -> EventType.VSC;
			case RED -> EventType.RED_FLAG;
			case YELLOW -> EventType.YELLOW_FLAG;
		};
	}

	private static Severity severity(TrackStatus status) {
		return switch (status) {
			case SAFETY_CAR, RED -> Severity.HIGH;
			case VSC, VSC_ENDING -> Severity.MEDIUM;
			case YELLOW -> Severity.LOW;
		};
	}

	private static String summary(TrackStatus status) {
		return switch (status) {
			case SAFETY_CAR -> "Safety car";
			case VSC, VSC_ENDING -> "Virtual safety car";
			case RED -> "Red flag";
			case YELLOW -> "Yellow flag";
		};
	}

}

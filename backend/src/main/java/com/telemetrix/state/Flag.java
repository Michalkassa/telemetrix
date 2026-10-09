package com.telemetrix.state;

import com.telemetrix.racecontrol.TrackStatus;

// What the track is under: green (racing) or one of the track statuses.
public enum Flag {
	GREEN, YELLOW, SAFETY_CAR, VSC, VSC_ENDING, RED;

	static Flag of(TrackStatus status) {
		return status == null ? GREEN : Flag.valueOf(status.name());
	}
}

package com.telemetrix.racecontrol;

// What the whole track is under, apart from green (racing): a yellow somewhere, the safety car, the
// virtual safety car (and its ending phase), or a red flag. As the F1 archive's TrackStatus codes
// 2, 4, 6, 7 and 5.
public enum TrackStatus {
	YELLOW, SAFETY_CAR, VSC, VSC_ENDING, RED
}

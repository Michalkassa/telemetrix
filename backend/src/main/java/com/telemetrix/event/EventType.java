package com.telemetrix.event;

// What happened. SAFETY_CAR, VSC, RED_FLAG and YELLOW_FLAG events last from `at` to `endsAt`.
public enum EventType {
	OVERTAKE, PIT_STOP, TYRE_CHANGE, FASTEST_LAP, SAFETY_CAR, VSC, RED_FLAG, YELLOW_FLAG, INCIDENT, CRASH, DNF,
	PENALTY, TEAM_RADIO, WEATHER
}

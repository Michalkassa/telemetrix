package com.telemetrix.state;

// Why a snapshot was taken (plan.md, Snapshotting): every 5 seconds, every lap, and at key events.
public enum SnapshotReason {
	START, INTERVAL, LAP, SAFETY_CAR, VSC, RED_FLAG, PIT_STOP, CHANGE;

	public boolean isKeyEvent() {
		return this == SAFETY_CAR || this == VSC || this == RED_FLAG || this == PIT_STOP;
	}
}

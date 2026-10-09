package com.telemetrix.racecontrol;

import java.time.Instant;

/** A stretch of the session under one track status; `to` is null if it lasted to the end of the data. */
public record TrackStatusPeriod(TrackStatus status, Instant from, Instant to) {
}

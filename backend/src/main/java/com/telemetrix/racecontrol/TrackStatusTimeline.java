package com.telemetrix.racecontrol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Turns a time-ordered series of track-status changes into periods. A status lasts until the next
// different one; green (null) is no period. The last period stays open (`to` null).
public final class TrackStatusTimeline {

	/** From `at`, the track is under `status` (null: green). */
	public record Change(Instant at, TrackStatus status) {
	}

	private TrackStatusTimeline() {
	}

	public static List<TrackStatusPeriod> periods(List<Change> changes) {
		List<TrackStatusPeriod> periods = new ArrayList<>();
		TrackStatus current = null;
		Instant since = null;
		for (Change change : changes) {
			if (Objects.equals(change.status(), current)) {
				continue;
			}
			if (current != null) {
				periods.add(new TrackStatusPeriod(current, since, change.at()));
			}
			current = change.status();
			since = change.at();
		}
		if (current != null) {
			periods.add(new TrackStatusPeriod(current, since, null));
		}
		return periods;
	}

}

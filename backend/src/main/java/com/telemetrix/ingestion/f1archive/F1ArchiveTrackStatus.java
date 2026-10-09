package com.telemetrix.ingestion.f1archive;

import com.telemetrix.racecontrol.TrackStatus;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import com.telemetrix.racecontrol.TrackStatusTimeline;
import com.telemetrix.racecontrol.TrackStatusTimeline.Change;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Reads the F1 live-timing archive's TrackStatus stream (docs/adr/0003) into track-status periods.
// A .jsonStream file has one line per update: "HH:MM:SS.mmm" (the offset from the start of the feed)
// followed by JSON. TrackStatus codes: 1 all clear, 2 yellow, 4 SC, 5 red, 6 VSC, 7 VSC ending.
// The offsets are anchored to UTC with SessionData, whose updates carry both an offset and a "Utc".
public final class F1ArchiveTrackStatus {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private F1ArchiveTrackStatus() {
	}

	public static List<TrackStatusPeriod> periods(String trackStatus, String sessionData) {
		Instant start = feedStart(sessionData);
		List<Change> changes = new ArrayList<>();
		for (Line line : lines(trackStatus)) {
			String code = line.json().path("Status").asString("");
			Instant at = start.plus(line.offset());
			switch (code) {
				case "1" -> changes.add(new Change(at, null));
				case "2" -> changes.add(new Change(at, TrackStatus.YELLOW));
				case "4" -> changes.add(new Change(at, TrackStatus.SAFETY_CAR));
				case "5" -> changes.add(new Change(at, TrackStatus.RED));
				case "6" -> changes.add(new Change(at, TrackStatus.VSC));
				case "7" -> changes.add(new Change(at, TrackStatus.VSC_ENDING));
				default -> {
					// unknown code: ignore
				}
			}
		}
		return TrackStatusTimeline.periods(changes);
	}

	/**
	 * The UTC moment the feed's offsets count from: the median of Utc − offset over SessionData's
	 * updates. Offset 0 is the snapshot at connection, whose Utc is older, so it's left out.
	 */
	public static Instant feedStart(String sessionData) {
		List<Long> starts = new ArrayList<>();
		for (Line line : lines(sessionData)) {
			if (line.offset().isZero()) {
				continue;
			}
			for (JsonNode utc : line.json().findValues("Utc")) {
				starts.add(Instant.parse(utc.asString()).minus(line.offset()).toEpochMilli());
			}
		}
		if (starts.isEmpty()) {
			throw new IllegalArgumentException("SessionData has no UTC times to anchor the feed");
		}
		starts.sort(null);
		return Instant.ofEpochMilli(starts.get(starts.size() / 2));
	}

	private record Line(Duration offset, JsonNode json) {
	}

	private static List<Line> lines(String stream) {
		List<Line> lines = new ArrayList<>();
		for (String raw : stream.replace("﻿", "").split("\\R")) {
			int brace = raw.indexOf('{');
			if (brace < 0) {
				continue;
			}
			lines.add(new Line(offset(raw.substring(0, brace).trim()), JSON.readTree(raw.substring(brace))));
		}
		return lines;
	}

	// "01:04:20.881"
	private static Duration offset(String text) {
		String[] parts = text.split(":");
		double seconds = Double.parseDouble(parts[2]);
		return Duration.ofHours(Long.parseLong(parts[0]))
				.plusMinutes(Long.parseLong(parts[1]))
				.plusMillis(Math.round(seconds * 1000));
	}

}

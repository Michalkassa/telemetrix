package com.telemetrix.event;

import com.telemetrix.event.RaceData.LapTime;
import com.telemetrix.event.RaceData.Message;
import com.telemetrix.event.RaceData.PenaltyIssued;
import com.telemetrix.event.RaceData.RadioClip;
import com.telemetrix.event.RaceData.Stint;
import com.telemetrix.event.RaceData.Weather;
import com.telemetrix.racecontrol.PenaltyKind;
import com.telemetrix.racecontrol.TrackStatus;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import com.telemetrix.replay.ProgressTimeline;
import com.telemetrix.replay.ProgressTimeline.TimingPoint;
import com.telemetrix.replay.RaceReplay.PitWindow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Tiny hand-built races for the detector tests. Times are seconds after 12:00:00 (t(90) = 12:01:30).
// A car's progress is given as (seconds, laps done) points, like the replay's timing points.
public class TestRace {

	public static final Instant START = Instant.parse("2023-09-17T12:00:00Z");

	private long sessionId;

	private int totalLaps = 10;

	private Instant chequeredAt;

	private final Map<Integer, String> codes = new LinkedHashMap<>();

	private final Map<Integer, ProgressTimeline> timelines = new LinkedHashMap<>();

	private final Map<Integer, List<PitWindow>> pits = new HashMap<>();

	private final List<LapTime> laps = new ArrayList<>();

	private final List<Stint> stints = new ArrayList<>();

	private final List<TrackStatusPeriod> trackStatus = new ArrayList<>();

	private final List<Message> raceControl = new ArrayList<>();

	private final List<PenaltyIssued> penalties = new ArrayList<>();

	private final List<RadioClip> radio = new ArrayList<>();

	private final List<Weather> weather = new ArrayList<>();

	public static Instant t(double seconds) {
		return START.plusMillis(Math.round(seconds * 1000));
	}

	public TestRace session(long sessionId) {
		this.sessionId = sessionId;
		return this;
	}

	public TestRace laps(int totalLaps) {
		this.totalLaps = totalLaps;
		return this;
	}

	public TestRace chequeredAt(double seconds) {
		this.chequeredAt = t(seconds);
		return this;
	}

	/** points: seconds, laps done, seconds, laps done, ... */
	public TestRace car(int number, String code, double... points) {
		codes.put(number, code);
		List<TimingPoint> list = new ArrayList<>();
		for (int i = 0; i < points.length; i += 2) {
			list.add(new TimingPoint(t(points[i]), points[i + 1]));
		}
		timelines.put(number, ProgressTimeline.of(list));
		return this;
	}

	public TestRace pit(int car, int lap, double from, double to) {
		pits.computeIfAbsent(car, c -> new ArrayList<>()).add(new PitWindow(lap, t(from), t(to)));
		return this;
	}

	public TestRace lap(int car, int lap, double startsAt, Integer lapMs) {
		laps.add(new LapTime(car, lap, t(startsAt), lapMs));
		return this;
	}

	public TestRace stint(int car, int stint, String compound, int lapStart) {
		return stint(car, stint, compound, lapStart, 0);
	}

	public TestRace stint(int car, int stint, String compound, int lapStart, int ageAtStart) {
		stints.add(new Stint(car, stint, compound, lapStart, ageAtStart));
		return this;
	}

	public TestRace status(TrackStatus status, double from, Double to) {
		trackStatus.add(new TrackStatusPeriod(status, t(from), to == null ? null : t(to)));
		return this;
	}

	public TestRace message(double at, int lap, String text) {
		raceControl.add(new Message(t(at), lap, text));
		return this;
	}

	public TestRace penalty(int car, PenaltyKind kind, Integer amount, String reason, double at, int lap) {
		penalties.add(new PenaltyIssued(car, kind, amount, reason, t(at), lap));
		return this;
	}

	public TestRace radio(int car, double at, String url) {
		radio.add(new RadioClip(car, t(at), url));
		return this;
	}

	public TestRace weather(double at, boolean rain) {
		return weather(at, rain, null, null);
	}

	public TestRace weather(double at, boolean rain, Double airTemperature, Double trackTemperature) {
		weather.add(new Weather(t(at), rain, airTemperature, trackTemperature, null, null));
		return this;
	}

	public RaceData build() {
		// the winner went furthest, first; the flag fell when they finished (as ReplayService)
		Instant flag = chequeredAt != null ? chequeredAt
				: timelines.values().stream()
						.max(java.util.Comparator.comparingDouble(ProgressTimeline::finalLapsDone)
								.thenComparing(ProgressTimeline::lastAt, java.util.Comparator.reverseOrder()))
						.map(ProgressTimeline::lastAt).orElse(START);
		return new RaceData(sessionId, START, flag, totalLaps, codes, timelines, pits, laps, stints, trackStatus, raceControl,
				penalties, radio, weather);
	}

}

package com.telemetrix.racecontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.racecontrol.TrackStatusTimeline.Change;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackStatusTimelineTest {

	static Instant at(String time) {
		return Instant.parse("2023-09-17T" + time + "Z");
	}

	@Test
	void eachStatusLastsUntilTheNextChangeAndGreenIsNoPeriod() {
		var periods = TrackStatusTimeline.periods(List.of(
				new Change(at("12:00:00"), null),
				new Change(at("12:05:00"), TrackStatus.YELLOW),
				new Change(at("12:05:10"), null),
				new Change(at("12:30:00"), TrackStatus.SAFETY_CAR),
				new Change(at("12:36:00"), null)));

		assertThat(periods).containsExactly(
				new TrackStatusPeriod(TrackStatus.YELLOW, at("12:05:00"), at("12:05:10")),
				new TrackStatusPeriod(TrackStatus.SAFETY_CAR, at("12:30:00"), at("12:36:00")));
	}

	@Test
	void theSameStatusAgainContinuesThePeriod() {
		var periods = TrackStatusTimeline.periods(List.of(
				new Change(at("12:05:00"), TrackStatus.YELLOW),
				new Change(at("12:05:05"), TrackStatus.YELLOW),
				new Change(at("12:06:00"), TrackStatus.VSC),
				new Change(at("12:08:00"), TrackStatus.VSC_ENDING)));

		assertThat(periods).containsExactly(
				new TrackStatusPeriod(TrackStatus.YELLOW, at("12:05:00"), at("12:06:00")),
				new TrackStatusPeriod(TrackStatus.VSC, at("12:06:00"), at("12:08:00")),
				new TrackStatusPeriod(TrackStatus.VSC_ENDING, at("12:08:00"), null)); // still going at the end
	}

}

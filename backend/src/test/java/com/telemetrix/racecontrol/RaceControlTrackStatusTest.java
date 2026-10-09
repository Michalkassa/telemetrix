package com.telemetrix.racecontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.Fixtures;
import com.telemetrix.racecontrol.RaceControlTrackStatus.Message;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

// Track status worked out from race-control messages: Singapore 2023 (session 9165).
class RaceControlTrackStatusTest {

	static Instant at(String time) {
		return Instant.parse("2023-09-17T" + time + "Z");
	}

	static List<Message> singapore() {
		return Fixtures.openF1RaceControl(9165).stream()
				.map(m -> new Message(m.date().toInstant(), m.message()))
				.toList();
	}

	@Test
	void theSafetyCarRunsFromDeployedToTrackClear() {
		assertThat(RaceControlTrackStatus.periods(singapore()))
				.contains(new TrackStatusPeriod(TrackStatus.SAFETY_CAR, at("12:36:41"), at("12:42:50")));
	}

	@Test
	void theVirtualSafetyCarAndItsEndingPhase() {
		assertThat(RaceControlTrackStatus.periods(singapore())).contains(
				new TrackStatusPeriod(TrackStatus.YELLOW, at("13:17:10"), at("13:18:06")),
				new TrackStatusPeriod(TrackStatus.VSC, at("13:18:06"), at("13:21:29")),
				new TrackStatusPeriod(TrackStatus.VSC_ENDING, at("13:21:29"), at("13:21:44")));
	}

	@Test
	void aYellowLastsUntilEverySectorIsClear() {
		// double yellow in 11, yellow in 10; 11 clears at :22, 10 at :27
		assertThat(RaceControlTrackStatus.periods(singapore()))
				.contains(new TrackStatusPeriod(TrackStatus.YELLOW, at("13:44:06"), at("13:44:27")));
	}

	@Test
	void aRedFlagLastsUntilTheTrackIsGreenAgain() {
		var periods = RaceControlTrackStatus.periods(List.of(
				new Message(at("12:00:00"), "RED FLAG"),
				new Message(at("12:30:00"), "GREEN LIGHT - PIT EXIT OPEN")));

		assertThat(periods).containsExactly(new TrackStatusPeriod(TrackStatus.RED, at("12:00:00"), at("12:30:00")));
	}

}

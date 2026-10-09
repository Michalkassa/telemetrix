package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Rain starting and stopping. The first reading is just how the session began.
class WeatherDetectorTest {

	@Test
	void rainStartingAndStopping() {
		var race = new TestRace().car(1, "VER", 0, 0, 1000, 10)
				.weather(0, false).weather(60, false).weather(120, true).weather(180, true).weather(600, false)
				.build();

		var events = new WeatherDetector().detect(race);

		assertThat(events).extracting(RaceEvent::at, RaceEvent::summary).containsExactly(
				org.assertj.core.groups.Tuple.tuple(t(120), "Rain started"),
				org.assertj.core.groups.Tuple.tuple(t(600), "Rain stopped"));
		assertThat(events).allSatisfy(e -> assertThat(e.type()).isEqualTo(EventType.WEATHER));
	}

	@Test
	void aSessionThatStartsWetIsNotAChange() {
		var race = new TestRace().car(1, "VER", 0, 0, 1000, 10).weather(0, true).weather(60, true).build();

		assertThat(new WeatherDetector().detect(race)).isEmpty();
	}

}

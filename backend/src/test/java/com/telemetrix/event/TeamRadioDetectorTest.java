package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TeamRadioDetectorTest {

	@Test
	void aMarkerForEachClipWithItsRecording() {
		var race = new TestRace().car(24, "ZHO", 0, 0, 1000, 10).radio(24, 250, "https://f1.test/radio.mp3").build();

		assertThat(new TeamRadioDetector().detect(race)).containsExactly(new RaceEvent(EventType.TEAM_RADIO, t(250), 3,
				null, null, Severity.LOW, List.of(24), "Team radio: ZHO", "https://f1.test/radio.mp3"));
	}

}

package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.racecontrol.TrackStatus;
import org.junit.jupiter.api.Test;

// Safety car, VSC, red and yellow flags: one event per period, from its start to its end.
class TrackStatusDetectorTest {

	// one car, 100 s a lap: lap 1 from 0, lap 2 from 100, ...
	TestRace race = new TestRace().car(1, "VER", 0, 0, 1000, 10);

	@Test
	void theSafetyCarFromDeployedToIn() {
		var events = new TrackStatusDetector().detect(race.status(TrackStatus.SAFETY_CAR, 250, 480.0).build());

		assertThat(events).containsExactly(new RaceEvent(EventType.SAFETY_CAR, t(250), 3, t(480), 5, Severity.HIGH,
				java.util.List.of(), "Safety car", null));
	}

	@Test
	void theVscAndItsEndingPhaseAreOneEvent() {
		var events = new TrackStatusDetector().detect(race.status(TrackStatus.VSC, 100, 200.0)
				.status(TrackStatus.VSC_ENDING, 200, 215.0).build());

		assertThat(events).singleElement().satisfies(vsc -> {
			assertThat(vsc.type()).isEqualTo(EventType.VSC);
			assertThat(vsc.at()).isEqualTo(t(100));
			assertThat(vsc.endsAt()).isEqualTo(t(215));
			assertThat(vsc.severity()).isEqualTo(Severity.MEDIUM);
		});
	}

	@Test
	void aRedFlag() {
		var events = new TrackStatusDetector().detect(race.status(TrackStatus.RED, 300, 900.0).build());

		assertThat(events).singleElement().satisfies(red -> {
			assertThat(red.type()).isEqualTo(EventType.RED_FLAG);
			assertThat(red.severity()).isEqualTo(Severity.HIGH);
			assertThat(red.lap()).isEqualTo(4);
		});
	}

	@Test
	void yellowFlagsAndOneStillOutAtTheEnd() {
		var events = new TrackStatusDetector().detect(race.status(TrackStatus.YELLOW, 10, 15.0)
				.status(TrackStatus.YELLOW, 990, null).build());

		assertThat(events).extracting(RaceEvent::type).containsExactly(EventType.YELLOW_FLAG, EventType.YELLOW_FLAG);
		assertThat(events.getLast().endsAt()).isNull();
		assertThat(events.getLast().endLap()).isNull();
		assertThat(events.getFirst().severity()).isEqualTo(Severity.LOW);
	}

}

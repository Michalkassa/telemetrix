package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.racecontrol.TrackStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

// Incidents the stewards note, and crashes — raised only when the signals agree (no source has a "crash"
// event, docs/adr/0003): a collision or "car stopped" message, a double yellow or SC/VSC/red right
// before or after it, and a car retiring or losing many places. Laps are 100 s.
class IncidentDetectorTest {

	static final String COLLISION = "TURN 13 INCIDENT INVOLVING CARS 11 (PER) AND 23 (ALB) NOTED - CAUSING A COLLISION";

	// five cars in number order, nose to tail; ALB (23) in P5
	TestRace race() {
		return new TestRace().laps(10)
				.car(1, "VER", 0, 0, 1000, 10)
				.car(11, "PER", 0, 0, 1000, 9.98)
				.car(16, "LEC", 0, 0, 1000, 9.96)
				.car(55, "SAI", 0, 0, 1000, 9.94);
	}

	@Test
	void aNotedIncidentIsAnIncidentOnce() {
		var events = new IncidentDetector().detect(race().car(23, "ALB", 0, 0, 1000, 9.92)
				.message(500, 6, COLLISION)
				.message(530, 6, "FIA STEWARDS: TURN 13 INCIDENT INVOLVING CARS 11 (PER) AND 23 (ALB) WILL BE INVESTIGATED AFTER THE RACE - CAUSING A COLLISION")
				.build());

		assertThat(events).singleElement().satisfies(e -> {
			assertThat(e.type()).isEqualTo(EventType.INCIDENT);
			assertThat(e.at()).isEqualTo(t(500));
			assertThat(e.lap()).isEqualTo(6);
			assertThat(e.participants()).isEqualTo(List.of(11, 23));
			assertThat(e.summary()).isEqualTo("Incident: PER, ALB — causing a collision");
			assertThat(e.severity()).isEqualTo(Severity.MEDIUM);
		});
	}

	@Test
	void aCollisionWithADoubleYellowAndACarRetiringIsACrash() {
		var events = new IncidentDetector().detect(race()
				.car(23, "ALB", 0, 0, 480, 4.75) // stops at 480 s
				.message(470, 5, "DOUBLE YELLOW IN TRACK SECTOR 11")
				.message(500, 6, COLLISION)
				.build());

		assertThat(events).singleElement().satisfies(e -> {
			assertThat(e.type()).isEqualTo(EventType.CRASH);
			assertThat(e.at()).isEqualTo(t(470)); // when the flags came out
			assertThat(e.participants()).isEqualTo(List.of(11, 23));
			assertThat(e.summary()).isEqualTo("Crash: PER, ALB — causing a collision");
			assertThat(e.severity()).isEqualTo(Severity.HIGH);
		});
	}

	@Test
	void aCarStoppedMessageWithTheSafetyCarAndPlacesLostIsACrash() {
		var events = new IncidentDetector().detect(race()
				// LEC runs P2, stops for a while at 440 s and is last (P5) two minutes later
				.car(16, "LEC", 0, 0, 440, 4.395, 450, 4.40, 520, 4.45, 1000, 9.0)
				.car(23, "ALB", 0, 0, 1000, 9.92)
				.status(TrackStatus.SAFETY_CAR, 460, 700.0)
				.message(455, 5, "CAR 16 (LEC) STOPPED AT TURN 8")
				.build());

		assertThat(events).singleElement().satisfies(e -> {
			assertThat(e.type()).isEqualTo(EventType.CRASH);
			assertThat(e.at()).isEqualTo(t(460));
			assertThat(e.participants()).isEqualTo(List.of(16));
		});
	}

	@Test
	void aCollisionWithoutFlagsIsOnlyAnIncident() {
		var events = new IncidentDetector().detect(race().car(23, "ALB", 0, 0, 480, 4.75).message(500, 6, COLLISION)
				.build());

		assertThat(events).extracting(RaceEvent::type).containsExactly(EventType.INCIDENT);
	}

	@Test
	void aCollisionWithFlagsButNoHarmDoneIsOnlyAnIncident() {
		var events = new IncidentDetector().detect(race().car(23, "ALB", 0, 0, 1000, 9.92)
				.message(470, 5, "DOUBLE YELLOW IN TRACK SECTOR 11")
				.message(500, 6, COLLISION)
				.build());

		assertThat(events).extracting(RaceEvent::type).containsExactly(EventType.INCIDENT);
	}

	@Test
	void aTrackLimitsNoteIsNothing() {
		var events = new IncidentDetector().detect(race().car(23, "ALB", 0, 0, 480, 4.75)
				.message(470, 5, "DOUBLE YELLOW IN TRACK SECTOR 11")
				.message(500, 6, "CAR 23 (ALB) TIME 1:41.829 DELETED - TRACK LIMITS AT TURN 7 LAP 5 20:12:55")
				.build());

		assertThat(events).isEmpty();
	}

}

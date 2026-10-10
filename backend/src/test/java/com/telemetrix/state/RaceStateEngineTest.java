package com.telemetrix.state;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.event.TestRace;
import com.telemetrix.racecontrol.PenaltyKind;
import com.telemetrix.racecontrol.TrackStatus;
import org.junit.jupiter.api.Test;

// Folding a race's events and timing into its state at a moment. Times are seconds after the start;
// VER does a lap every 100 s.
class RaceStateEngineTest {

	static CarState car(RaceState state, int number) {
		return state.cars().stream().filter(c -> c.number() == number).findFirst().orElseThrow();
	}

	@Test
	void orderAndGapsToTheLeaderAndTheCarAhead() {
		var engine = RaceStateEngine.of(new TestRace().laps(10)
				.car(1, "VER", 0, 0, 1000, 10)
				.car(44, "HAM", 0, 0, 1000, 9.5)
				.car(2, "SAR", 0, 0, 1000, 8.5)
				.build());

		var state = engine.stateAt(t(500)); // VER 5.0 laps, HAM 4.75, SAR 4.25

		assertThat(state.cars()).extracting(CarState::code).containsExactly("VER", "HAM", "SAR");
		assertThat(state.cars()).extracting(CarState::position).containsExactly(1, 2, 3);
		assertThat(car(state, 1).gapToLeader()).isEqualTo(0.0);
		assertThat(car(state, 1).interval()).isNull();
		assertThat(car(state, 44).gapToLeader()).isEqualTo(25.0); // VER was at 4.75 laps at 475 s
		assertThat(car(state, 44).interval()).isEqualTo(25.0);
		assertThat(car(state, 2).gapToLeader()).isEqualTo(75.0);
		assertThat(car(state, 2).interval()).isCloseTo(52.632, org.assertj.core.api.Assertions.within(0.001));
		assertThat(car(state, 2).lapsDown()).isZero();
		assertThat(state.lap()).isEqualTo(6);
	}

	@Test
	void aLappedCarIsLapsDown() {
		var engine = RaceStateEngine.of(new TestRace().laps(10)
				.car(1, "VER", 0, 0, 1000, 10)
				.car(2, "SAR", 0, 0, 1000, 8.5)
				.build());

		var sar = car(engine.stateAt(t(900)), 2); // VER 9.0, SAR 7.65

		assertThat(sar.lapsDown()).isEqualTo(1);
		assertThat(sar.gapToLeader()).isEqualTo(135.0);
	}

	@Test
	void tyresAndTheirAgeChangeAtThePitStop() {
		var engine = RaceStateEngine.of(new TestRace().laps(20)
				.car(1, "VER", 0, 0, 2000, 20)
				.stint(1, 1, "MEDIUM", 1).stint(1, 2, "HARD", 11, 2)
				.pit(1, 10, 950, 975)
				.build());

		assertThat(car(engine.stateAt(t(900)), 1)).satisfies(c -> { // lap 10
			assertThat(c.compound()).isEqualTo("MEDIUM");
			assertThat(c.tyreAge()).isEqualTo(9);
			assertThat(c.pitStops()).isZero();
			assertThat(c.status()).isEqualTo(CarStatus.RUNNING);
		});
		assertThat(car(engine.stateAt(t(960)), 1)).satisfies(c -> {
			assertThat(c.status()).isEqualTo(CarStatus.IN_PIT);
			assertThat(c.pitStops()).isEqualTo(1);
		});
		assertThat(car(engine.stateAt(t(1500)), 1)).satisfies(c -> { // lap 16 on tyres 2 laps old when fitted
			assertThat(c.compound()).isEqualTo("HARD");
			assertThat(c.tyreAge()).isEqualTo(7);
			assertThat(c.status()).isEqualTo(CarStatus.RUNNING);
		});
	}

	@Test
	void theFlagFollowsTheTrackStatus() {
		var engine = RaceStateEngine.of(new TestRace().laps(10).car(1, "VER", 0, 0, 1000, 10)
				.status(TrackStatus.YELLOW, 100, 120.0)
				.status(TrackStatus.SAFETY_CAR, 300, 500.0)
				.status(TrackStatus.VSC, 600, 650.0).status(TrackStatus.VSC_ENDING, 650, 660.0)
				.build());

		assertThat(engine.stateAt(t(50)).flag()).isEqualTo(Flag.GREEN);
		assertThat(engine.stateAt(t(110)).flag()).isEqualTo(Flag.YELLOW);
		assertThat(engine.stateAt(t(400)).flag()).isEqualTo(Flag.SAFETY_CAR);
		assertThat(engine.stateAt(t(500)).flag()).isEqualTo(Flag.GREEN);
		assertThat(engine.stateAt(t(620)).flag()).isEqualTo(Flag.VSC);
		assertThat(engine.stateAt(t(655)).flag()).isEqualTo(Flag.VSC_ENDING);
		assertThat(engine.stateAt(t(700)).flag()).isEqualTo(Flag.GREEN);
	}

	@Test
	void timePenaltiesAddUpAndADriveThroughIsServedAtTheNextStop() {
		var engine = RaceStateEngine.of(new TestRace().laps(10).car(14, "ALO", 0, 0, 1000, 10)
				.penalty(14, PenaltyKind.TIME, 5, "CAUSING A COLLISION", 300, 4)
				.penalty(14, PenaltyKind.TIME, 10, "UNSAFE RELEASE", 400, 5)
				.penalty(14, PenaltyKind.DRIVE_THROUGH, null, "SPEEDING IN THE PIT LANE", 500, 6)
				.pit(14, 7, 650, 670)
				.build());

		assertThat(car(engine.stateAt(t(299)), 14).penaltySeconds()).isZero();
		assertThat(car(engine.stateAt(t(450)), 14).penaltySeconds()).isEqualTo(15);
		assertThat(car(engine.stateAt(t(600)), 14).pendingPenalties()).isEqualTo(1);
		assertThat(car(engine.stateAt(t(700)), 14)).satisfies(c -> {
			assertThat(c.pendingPenalties()).isZero();
			assertThat(c.penaltySeconds()).isEqualTo(15); // time penalties are added after the race
		});
	}

	@Test
	void aRetiredCarDropsToTheBackWithoutGaps() {
		var engine = RaceStateEngine.of(new TestRace().laps(10)
				.car(1, "VER", 0, 0, 1000, 10)
				.car(31, "OCO", 0, 0, 300, 3.5) // ahead of HAM when it stops at 300 s
				.car(44, "HAM", 0, 0, 1000, 9.5)
				.build());

		var state = engine.stateAt(t(320)); // VER 3.2, OCO 3.5 (stopped), HAM 3.04

		assertThat(state.cars()).extracting(CarState::code).containsExactly("VER", "HAM", "OCO");
		assertThat(car(state, 31)).satisfies(c -> {
			assertThat(c.status()).isEqualTo(CarStatus.RETIRED);
			assertThat(c.position()).isEqualTo(3);
			assertThat(c.gapToLeader()).isNull();
			assertThat(c.interval()).isNull();
		});
	}

	@Test
	void beforeTheStartEveryCarIsOnTheGridAndAfterTheFlagFinished() {
		var engine = RaceStateEngine.of(new TestRace().laps(10)
				.car(1, "VER", 10, 0, 1000, 10)
				.car(44, "HAM", 10, 0, 1040, 10)
				.build());

		assertThat(engine.stateAt(t(0)).cars()).extracting(CarState::status).containsOnly(CarStatus.ON_GRID);
		var end = engine.stateAt(t(2000));
		assertThat(end.cars()).extracting(CarState::status).containsOnly(CarStatus.FINISHED);
		assertThat(car(end, 44).gapToLeader()).isEqualTo(40.0); // frozen when HAM finished
	}

	@Test
	void afterTheFlagCarsOnTheSameLapAreInTheOrderTheyFinished() {
		// both complete 10 laps; HAM (44) crosses the line 5 s before VER (1) — car numbers mustn't decide
		var engine = RaceStateEngine.of(new TestRace().laps(10)
				.car(44, "HAM", 0, 0, 1000, 10)
				.car(1, "VER", 0, 0, 1005, 10)
				.car(16, "LEC", 0, 0, 1100, 9.8) // a lap down... still behind
				.build());

		assertThat(engine.stateAt(t(2000)).cars()).extracting(CarState::code).containsExactly("HAM", "VER", "LEC");
	}

}

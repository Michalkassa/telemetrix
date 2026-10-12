package com.telemetrix.state;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemetrix.event.TestRace;
import com.telemetrix.racecontrol.PenaltyKind;
import com.telemetrix.racecontrol.TrackStatus;
import com.telemetrix.state.SessionEvent.PitIn;
import com.telemetrix.state.SessionEvent.PitOut;
import com.telemetrix.state.SessionEvent.SessionRecorded;
import com.telemetrix.state.SessionEvent.TyresFitted;
import java.util.List;
import org.junit.jupiter.api.Test;

// Time travel debugging: the race state right after any event in the stream, by its number — even
// between events that happened at the same moment.
class TimeTravelTest {

	// VER pits on lap 10 (950-975 s) and leaves on hards; ALO gets a drive-through at 500 s
	RaceStateEngine engine = RaceStateEngine.of(new TestRace().laps(20)
			.car(1, "VER", 0, 0, 2000, 20)
			.car(14, "ALO", 0, 0, 2050, 20)
			.stint(1, 1, "MEDIUM", 1).stint(1, 2, "HARD", 11)
			.pit(1, 10, 950, 975)
			.penalty(14, PenaltyKind.DRIVE_THROUGH, null, "SPEEDING", 500, 5)
			.status(TrackStatus.SAFETY_CAR, 900, 1000.0)
			.build());

	@Test
	void theStreamOpensWithTheSessionThenTheStartingTyresThenEventsInTimeOrder() {
		List<SessionEvent> events = engine.events();

		assertThat(events.getFirst()).isInstanceOf(SessionRecorded.class);
		assertThat(events.get(1)).isEqualTo(new TyresFitted(null, 1, "MEDIUM", 1, 0));
		var timed = events.stream().filter(e -> e.at() != null).map(SessionEvent::at).toList();
		assertThat(timed).isSorted();
	}

	@Test
	void stopsBetweenEventsAtTheSameMoment() {
		List<SessionEvent> events = engine.events();
		int pitOut = events.indexOf(new PitOut(t(975), 1)) + 1; // event numbers start at 1
		int tyres = events.indexOf(new TyresFitted(t(975), 1, "HARD", 11, 0)) + 1;
		assertThat(tyres).isEqualTo(pitOut + 1); // the car leaves, then its new tyres count

		var afterPitOut = RaceStateEngineTest.car(engine.stateAtEvent(pitOut), 1);
		var afterTyres = RaceStateEngineTest.car(engine.stateAtEvent(tyres), 1);

		assertThat(afterPitOut.status()).isEqualTo(CarStatus.RUNNING);
		assertThat(afterPitOut.compound()).isEqualTo("MEDIUM");
		assertThat(afterTyres.compound()).isEqualTo("HARD");
	}

	@Test
	void theStateAtAnEventIncludesItAndNothingAfter() {
		int pitIn = engine.events().indexOf(new PitIn(t(950), 1)) + 1;

		RaceState state = engine.stateAtEvent(pitIn);

		assertThat(state.at()).isEqualTo(t(950));
		assertThat(state.flag()).isEqualTo(Flag.SAFETY_CAR);
		assertThat(RaceStateEngineTest.car(state, 1).status()).isEqualTo(CarStatus.IN_PIT);
		assertThat(RaceStateEngineTest.car(state, 1).pitStops()).isEqualTo(1);
		assertThat(RaceStateEngineTest.car(state, 14).pendingPenalties()).isEqualTo(1);
	}

	@Test
	void anEventBeforeTheStartIsAtTheClocksZeroWithOnlyTheStartingTyres() {
		RaceState state = engine.stateAtEvent(2);

		assertThat(state.at()).isEqualTo(t(0));
		assertThat(state.flag()).isEqualTo(Flag.GREEN);
		assertThat(state.cars()).extracting(CarState::pitStops).containsOnly(0);
		assertThat(RaceStateEngineTest.car(state, 1).compound()).isEqualTo("MEDIUM");
	}

	@Test
	void anEventNumberOutsideTheStreamIsRejected() {
		assertThatThrownBy(() -> engine.stateAtEvent(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> engine.stateAtEvent(engine.events().size() + 1))
				.isInstanceOf(IllegalArgumentException.class);
	}

}

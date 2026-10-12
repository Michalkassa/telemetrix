package com.telemetrix.state;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.event.TestRace;
import com.telemetrix.racecontrol.PenaltyKind;
import com.telemetrix.racecontrol.TrackStatus;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

// Time travel is deterministic: the state at event #n depends only on the events, and the state after the
// last event at a moment is the state at that moment.
class TimeTravelProperties {

	record Stop(int car, int lap, double from, double seconds) {
	}

	record Penalty(int car, PenaltyKind kind, double at) {
	}

	@Property(tries = 200)
	void theStateAtEventNIsDeterministic(@ForAll("stops") List<Stop> stops, @ForAll("penalties") List<Penalty> penalties,
			@ForAll("statuses") TrackStatus status, @ForAll("fractions") double where) {
		var first = RaceStateEngine.of(race(stops, penalties, status));
		var second = RaceStateEngine.of(race(stops, penalties, status));
		int n = 1 + (int) (where * (first.events().size() - 1));

		assertThat(second.events()).isEqualTo(first.events());
		assertThat(first.stateAtEvent(n)).isEqualTo(second.stateAtEvent(n));
		assertThat(first.stateAtEvent(n)).isEqualTo(first.stateAtEvent(n));
	}

	@Property(tries = 200)
	void afterTheLastEventAtAMomentTheStateIsTheStateThen(@ForAll("stops") List<Stop> stops,
			@ForAll("penalties") List<Penalty> penalties, @ForAll("statuses") TrackStatus status,
			@ForAll("fractions") double where) {
		var engine = RaceStateEngine.of(race(stops, penalties, status));
		var events = engine.events();
		int n = 1 + (int) (where * (events.size() - 1));
		var at = events.get(n - 1).at();
		while (at != null && n < events.size() && at.equals(events.get(n).at())) {
			n++; // the last event at that moment
		}

		if (at != null) {
			assertThat(engine.stateAtEvent(n)).isEqualTo(engine.stateAt(at));
		}
	}

	private static com.telemetrix.event.RaceData race(List<Stop> stops, List<Penalty> penalties, TrackStatus status) {
		var race = new TestRace().laps(10)
				.car(1, "VER", 0, 0, 1000, 10)
				.car(44, "HAM", 0, 0, 1010, 10)
				.stint(1, 1, "MEDIUM", 1).stint(1, 2, "HARD", 5)
				.stint(44, 1, "SOFT", 1).stint(44, 2, "MEDIUM", 4, 3)
				.status(status, 300, 400.0)
				.message(310, 4, "DOUBLE YELLOW IN TRACK SECTOR 7")
				.message(320, 4, "CLEAR IN TRACK SECTOR 7")
				.message(500, 6, "DRS ENABLED")
				.weather(100, false, 30.0, 40.0).weather(600, true, 25.0, 30.0);
		stops.forEach(s -> race.pit(s.car(), s.lap(), s.from(), s.from() + s.seconds()));
		penalties.forEach(p -> race.penalty(p.car(), p.kind(), p.kind() == PenaltyKind.DRIVE_THROUGH ? null : 5,
				"REASON", p.at(), 1));
		return race.build();
	}

	@Provide
	Arbitrary<List<Stop>> stops() {
		return Combinators.combine(Arbitraries.of(1, 44), Arbitraries.integers().between(1, 10),
				Arbitraries.doubles().between(0, 1000).ofScale(0), Arbitraries.doubles().between(18, 40).ofScale(0))
				.as(Stop::new).list().ofMaxSize(4);
	}

	@Provide
	Arbitrary<List<Penalty>> penalties() {
		return Combinators.combine(Arbitraries.of(1, 44), Arbitraries.of(PenaltyKind.values()),
				Arbitraries.doubles().between(0, 1000).ofScale(0))
				.as(Penalty::new).list().ofMaxSize(3);
	}

	@Provide
	Arbitrary<TrackStatus> statuses() {
		return Arbitraries.of(TrackStatus.values());
	}

	@Provide
	Arbitrary<Double> fractions() {
		return Arbitraries.doubles().between(0, 1);
	}

}

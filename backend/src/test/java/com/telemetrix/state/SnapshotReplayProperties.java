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

// For any race and any moment, the state from the nearest snapshot plus the events since equals the state
// folded from scratch: snapshots make seeking fast without changing the answer.
class SnapshotReplayProperties {

	record Stop(int car, int lap, double from, double seconds) {
	}

	record Penalty(int car, PenaltyKind kind, double at) {
	}

	record Period(TrackStatus status, double from, double seconds) {
	}

	@Property(tries = 300)
	void snapshotPlusReplayEqualsFromScratch(@ForAll("stops") List<Stop> stops,
			@ForAll("penalties") List<Penalty> penalties, @ForAll("periods") List<Period> periods,
			@ForAll("moments") double seconds) {
		var race = new TestRace().laps(10)
				.car(1, "VER", 0, 0, 1000, 10)
				.car(44, "HAM", 0, 0, 1010, 10)
				.car(16, "LEC", 0, 0, 600, 5.8) // retires
				.stint(1, 1, "MEDIUM", 1).stint(1, 2, "HARD", 5)
				.stint(44, 1, "SOFT", 1).stint(44, 2, "MEDIUM", 4, 3);
		stops.forEach(s -> race.pit(s.car(), s.lap(), s.from(), s.from() + s.seconds()));
		penalties.forEach(p -> race.penalty(p.car(), p.kind(), p.kind() == PenaltyKind.DRIVE_THROUGH ? null : 5,
				"REASON", p.at(), 1));
		double end = 0;
		for (Period p : periods.stream().sorted(java.util.Comparator.comparingDouble(Period::from)).toList()) {
			double from = Math.max(p.from(), end); // periods don't overlap
			race.status(p.status(), from, from + p.seconds());
			end = from + p.seconds();
		}
		var engine = RaceStateEngine.of(race.build());

		assertThat(engine.stateAt(t(seconds))).isEqualTo(engine.stateFromScratch(t(seconds)));
	}

	@Provide
	Arbitrary<List<Stop>> stops() {
		return Combinators.combine(Arbitraries.of(1, 44, 16), Arbitraries.integers().between(1, 10),
				Arbitraries.doubles().between(0, 1000).ofScale(1), Arbitraries.doubles().between(18, 40).ofScale(1))
				.as(Stop::new).list().ofMaxSize(5);
	}

	@Provide
	Arbitrary<List<Penalty>> penalties() {
		return Combinators.combine(Arbitraries.of(1, 44, 16), Arbitraries.of(PenaltyKind.values()),
				Arbitraries.doubles().between(0, 1000).ofScale(1))
				.as(Penalty::new).list().ofMaxSize(4);
	}

	@Provide
	Arbitrary<List<Period>> periods() {
		return Combinators.combine(Arbitraries.of(TrackStatus.values()), Arbitraries.doubles().between(0, 1000).ofScale(1),
				Arbitraries.doubles().between(1, 200).ofScale(1))
				.as(Period::new).list().ofMaxSize(4);
	}

	@Provide
	Arbitrary<Double> moments() {
		return Arbitraries.doubles().between(-60, 1100).ofScale(3);
	}

}

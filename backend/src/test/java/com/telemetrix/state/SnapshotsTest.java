package com.telemetrix.state;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.event.TestRace;
import com.telemetrix.racecontrol.TrackStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;

// When snapshots are taken (plan.md, Snapshotting), and finding the one to start a seek from.
class SnapshotsTest {

	// three 100 s laps from 0 s; a pit stop at 150 s, the SC from 120 s, a red flag at 250 s
	RaceStateEngine engine = RaceStateEngine.of(new TestRace().laps(3)
			.car(1, "VER", 0, 0, 300, 3)
			.car(44, "HAM", 0, 0, 310, 3)
			.pit(44, 2, 150, 170)
			.status(TrackStatus.SAFETY_CAR, 120, 200.0)
			.status(TrackStatus.RED, 250, 260.0)
			.build());

	@Test
	void oncePerLapWhenTheLeaderStartsIt() {
		assertThat(engine.snapshots()).filteredOn(s -> s.reason() == SnapshotReason.LAP)
				.extracting(Snapshot::at, Snapshot::lap)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(t(0), 1),
						org.assertj.core.groups.Tuple.tuple(t(100), 2),
						org.assertj.core.groups.Tuple.tuple(t(200), 3));
	}

	@Test
	void atEachKeyEvent() {
		assertThat(engine.snapshots()).filteredOn(s -> s.reason().isKeyEvent())
				.extracting(Snapshot::reason, Snapshot::at)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(SnapshotReason.SAFETY_CAR, t(120)),
						org.assertj.core.groups.Tuple.tuple(SnapshotReason.PIT_STOP, t(150)),
						org.assertj.core.groups.Tuple.tuple(SnapshotReason.RED_FLAG, t(250)));
	}

	@Test
	void everyFiveSecondsInBetween() {
		var interval = engine.snapshots().stream().filter(s -> s.reason() == SnapshotReason.INTERVAL).toList();

		assertThat(interval).isNotEmpty();
		for (int i = 1; i < interval.size(); i++) {
			assertThat(Duration.between(interval.get(i - 1).at(), interval.get(i).at())).isEqualTo(Duration.ofSeconds(5));
		}
		assertThat(interval.getFirst().at()).isEqualTo(t(0));
		assertThat(interval.getLast().at()).isAfterOrEqualTo(t(305));
	}

	@Test
	void aSnapshotHoldsTheStateAfterEverythingUpToItsTime() {
		var pit = engine.snapshots().stream().filter(s -> s.reason() == SnapshotReason.PIT_STOP).findFirst()
				.orElseThrow();

		assertThat(pit.state().flag()).isEqualTo(Flag.SAFETY_CAR);
		assertThat(pit.state().car(44).pitStops()).isEqualTo(1);
		assertThat(pit.state().car(44).inPit()).isTrue();
	}

	@Test
	void seekingStartsFromTheLatestSnapshotAtOrBeforeTheMoment() {
		assertThat(engine.nearestSnapshot(t(150)).at()).isEqualTo(t(150));
		assertThat(engine.nearestSnapshot(t(152.5)).at()).isEqualTo(t(150));
		assertThat(engine.nearestSnapshot(t(157)).at()).isEqualTo(t(155));
	}

	@Test
	void beforeTheFirstSnapshotTheStartingState() {
		var start = engine.nearestSnapshot(t(-60));

		assertThat(start.reason()).isEqualTo(SnapshotReason.START);
		assertThat(start.state().flag()).isEqualTo(Flag.GREEN);
	}

}

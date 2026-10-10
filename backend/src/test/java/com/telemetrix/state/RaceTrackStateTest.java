package com.telemetrix.state;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.event.TestRace;
import java.util.Map;
import org.junit.jupiter.api.Test;

// The track's state for the overlays: yellow marshalling sectors, DRS, weather, and each car's DRS and
// mandatory-stop status. VER does a lap every 100 s.
class RaceTrackStateTest {

	static CarState car(RaceState state, int number) {
		return state.cars().stream().filter(c -> c.number() == number).findFirst().orElseThrow();
	}

	@Test
	void yellowSectorsFollowTheMarshalsFlags() {
		var engine = RaceStateEngine.of(new TestRace().laps(10).car(1, "VER", 0, 0, 1000, 10)
				.message(100, 2, "YELLOW IN TRACK SECTOR 7")
				.message(105, 2, "DOUBLE YELLOW IN TRACK SECTOR 7")
				.message(106, 2, "YELLOW IN TRACK SECTOR 8")
				.message(120, 2, "CLEAR IN TRACK SECTOR 7")
				.message(300, 4, "YELLOW IN TRACK SECTOR 2")
				.message(310, 4, "TRACK CLEAR")
				.build());

		assertThat(engine.stateAt(t(99)).yellowSectors()).isEmpty();
		assertThat(engine.stateAt(t(101)).yellowSectors()).isEqualTo(Map.of(7, SectorFlag.YELLOW));
		assertThat(engine.stateAt(t(110)).yellowSectors())
				.isEqualTo(Map.of(7, SectorFlag.DOUBLE_YELLOW, 8, SectorFlag.YELLOW));
		assertThat(engine.stateAt(t(130)).yellowSectors()).isEqualTo(Map.of(8, SectorFlag.YELLOW));
		assertThat(engine.stateAt(t(305)).yellowSectors()).containsOnlyKeys(8, 2);
		assertThat(engine.stateAt(t(311)).yellowSectors()).isEmpty(); // track clear: every sector
	}

	@Test
	void drsIsEnabledByRaceControlAndACarHasItWithinASecondOfTheCarAhead() {
		var engine = RaceStateEngine.of(new TestRace().laps(10)
				.car(1, "VER", 0, 0, 1000, 10)
				.car(44, "HAM", 0, 0, 1002, 10) // 0.2 s a lap slower: 0.6 s behind at 300 s
				.car(16, "LEC", 0, 0, 1100, 10) // far behind HAM
				.message(250, 3, "DRS ENABLED")
				.message(600, 7, "DRS DISABLED")
				.message(650, 7, "DRS ENABLED IN ZONE 2") // a single zone: not DRS as a whole
				.build());

		var early = engine.stateAt(t(150)); // HAM 0.3 s behind, but DRS isn't enabled yet
		assertThat(early.drsEnabled()).isFalse();
		assertThat(car(early, 44).drs()).isFalse();

		var on = engine.stateAt(t(300));
		assertThat(on.drsEnabled()).isTrue();
		assertThat(car(on, 44).interval()).isEqualTo(0.599);
		assertThat(car(on, 44).drs()).isTrue();
		assertThat(car(on, 16).drs()).isFalse();
		assertThat(car(on, 1).drs()).isFalse(); // the leader has no one to chase

		var off = engine.stateAt(t(700));
		assertThat(off.drsEnabled()).isFalse();
		assertThat(car(off, 44).drs()).isFalse();
	}

	@Test
	void theWeatherIsTheLatestReading() {
		var engine = RaceStateEngine.of(new TestRace().laps(10).car(1, "VER", 0, 0, 1000, 10)
				.weather(0, false, 30.0, 38.3)
				.weather(60, false, 30.1, 38.0)
				.weather(500, true, 27.5, 31.2)
				.build());

		assertThat(engine.stateAt(t(-10)).weather()).isNull();
		assertThat(engine.stateAt(t(100)).weather()).isEqualTo(new WeatherNow(30.1, 38.0, null, false, null));
		assertThat(engine.stateAt(t(600)).weather().rainfall()).isTrue();
		assertThat(engine.stateAt(t(600)).weather().trackTemperature()).isEqualTo(31.2);
	}

	@Test
	void theMandatoryStopIsDoneOnceTwoDryCompoundsHaveBeenUsed() {
		var engine = RaceStateEngine.of(new TestRace().laps(20)
				.car(1, "VER", 0, 0, 2000, 20)
				.car(44, "HAM", 0, 0, 2000, 19.9)
				.car(16, "LEC", 0, 0, 2000, 19.8)
				.stint(1, 1, "MEDIUM", 1).stint(1, 2, "HARD", 11).pit(1, 10, 950, 975)
				.stint(44, 1, "MEDIUM", 1).stint(44, 2, "MEDIUM", 11).pit(44, 10, 960, 985) // same compound again
				.stint(16, 1, "INTERMEDIATE", 1) // a wet race: no rule
				.build());

		assertThat(car(engine.stateAt(t(900)), 1).mandatoryStopDone()).isFalse();
		var after = engine.stateAt(t(1100));
		assertThat(car(after, 1).mandatoryStopDone()).isTrue();
		assertThat(car(after, 44).mandatoryStopDone()).isFalse();
		assertThat(car(after, 16).mandatoryStopDone()).isTrue();
	}

	@Test
	void aSnapshotAtEveryChangeForTheBrowser() {
		var engine = RaceStateEngine.of(new TestRace().laps(10).car(1, "VER", 0, 0, 1000, 10)
				.message(102.5, 2, "YELLOW IN TRACK SECTOR 7")
				.message(250, 3, "DRS ENABLED")
				.build());

		assertThat(engine.snapshots()).filteredOn(s -> s.reason() == SnapshotReason.CHANGE)
				.extracting(Snapshot::at).containsExactly(t(102.5), t(250));
		assertThat(engine.nearestSnapshot(t(103)).state().yellowSectors()).containsOnlyKeys(7);
	}

}

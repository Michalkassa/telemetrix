package com.telemetrix.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.replay.PlaybackFrame.Status;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// One moment of the race: every car's position, lap and place on the track.
class PlaybackFrameTest {

	static final Instant T = Instant.parse("2024-03-02T15:30:00Z");

	@Test
	void carsAreOrderedByHowFarRoundTheRaceTheyAre() {
		var frame = PlaybackFrame.at(T, 57)
				.car(car(16, 9.40, "15:29:00"))
				.car(car(1, 9.95, "15:29:30"))
				.car(car(11, 9.10, "15:28:00"))
				.build();

		assertThat(frame.cars()).extracting(PlaybackFrame.Car::number).containsExactly(1, 16, 11);
		assertThat(frame.cars()).extracting(PlaybackFrame.Car::position).containsExactly(1, 2, 3);
		assertThat(frame.leaderLap()).isEqualTo(10);
	}

	@Test
	void carsThatStoppedAtTheSameDistanceAreOrderedByWhoGotThereFirst() {
		// two lapped cars that both finished on 56 laps: the first across the line is ahead
		var frame = PlaybackFrame.at(T, 57)
				.car(new PlaybackFrame.CarState(10, 56.0, Instant.parse("2024-03-02T16:36:30Z"), Status.FINISHED, 0, 0))
				.car(new PlaybackFrame.CarState(31, 56.0, Instant.parse("2024-03-02T16:36:20Z"), Status.FINISHED, 0, 0))
				.build();

		assertThat(frame.cars()).extracting(PlaybackFrame.Car::number).containsExactly(31, 10);
	}

	@Test
	void theCurrentLapAndProgressComeFromLapsDone() {
		var frame = PlaybackFrame.at(T, 57).car(car(1, 9.25, "15:29:00")).car(car(16, 10.0, "15:29:30")).build();

		var max = frame.cars().stream().filter(c -> c.number() == 1).findFirst().orElseThrow();
		assertThat(max.lap()).isEqualTo(10);
		assertThat(max.progress()).isEqualTo(0.25);
		var charles = frame.cars().getFirst(); // exactly on the line after 10 laps: starting lap 11
		assertThat(charles.lap()).isEqualTo(11);
		assertThat(charles.progress()).isZero();
	}

	@Test
	void aFinishedCarShowsTheLapsItCompleted() {
		var frame = PlaybackFrame.at(T, 57)
				.car(new PlaybackFrame.CarState(1, 57.0, T, Status.FINISHED, 0, 0))
				.build();

		assertThat(frame.cars().getFirst().lap()).isEqualTo(57);
		assertThat(frame.cars().getFirst().status()).isEqualTo(Status.FINISHED);
	}

	private static PlaybackFrame.CarState car(int number, double lapsDone, String reached) {
		return new PlaybackFrame.CarState(number, lapsDone, Instant.parse("2024-03-02T" + reached + "Z"), Status.RUNNING,
				0, 0);
	}

}

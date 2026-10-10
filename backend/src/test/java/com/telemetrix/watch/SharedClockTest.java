package com.telemetrix.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemetrix.FakeTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// The server-held playback clock of one watch session: race time in ms, moved only by the host.
class SharedClockTest {

	static final Instant NOW = Instant.parse("2026-10-09T20:00:00Z");

	FakeTime time = new FakeTime(NOW);

	SharedClock clock = new SharedClock("w1", 5, time);

	@Test
	void startsPausedAtTheStart() {
		assertThat(clock.state()).isEqualTo(new ClockState("w1", 5, 0, false, 1.0, 0, NOW, NOW));
	}

	@Test
	void playingMovesRaceTimeOnWithTheServersClock() {
		clock.play();
		time.advance(Duration.ofSeconds(10));

		assertThat(clock.positionMs()).isEqualTo(10_000);
		assertThat(clock.state().positionMs()).isZero(); // the state says: from 0 at NOW
		assertThat(clock.state().at()).isEqualTo(NOW);
		assertThat(clock.state().serverTime()).isEqualTo(NOW.plusSeconds(10));
	}

	@Test
	void pauseFreezesItAndPlayCarriesOn() {
		clock.play();
		time.advance(Duration.ofSeconds(10));
		var paused = clock.pause();
		time.advance(Duration.ofMinutes(5));

		assertThat(paused.positionMs()).isEqualTo(10_000);
		assertThat(clock.positionMs()).isEqualTo(10_000);
		clock.play();
		time.advance(Duration.ofSeconds(1));
		assertThat(clock.positionMs()).isEqualTo(11_000);
	}

	@Test
	void seekingWhilePlayingCarriesOnFromThere() {
		clock.play();
		time.advance(Duration.ofSeconds(10));
		clock.seek(600_000);
		time.advance(Duration.ofSeconds(2));

		assertThat(clock.positionMs()).isEqualTo(602_000);
	}

	@Test
	void aSpeedChangeKeepsThePositionAndThenGoesFaster() {
		clock.play();
		time.advance(Duration.ofSeconds(10));
		var faster = clock.speed(4);
		time.advance(Duration.ofSeconds(10));

		assertThat(faster.positionMs()).isEqualTo(10_000);
		assertThat(clock.positionMs()).isEqualTo(50_000);
	}

	@Test
	void cannotSeekBeforeTheStart() {
		assertThat(clock.seek(-5_000).positionMs()).isZero();
	}

	@Test
	void speedsFromAQuarterToEightTimes() {
		assertThat(clock.speed(0.25).speed()).isEqualTo(0.25);
		assertThat(clock.speed(8).speed()).isEqualTo(8);
		assertThatThrownBy(() -> clock.speed(16)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> clock.speed(0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void everyChangeIsNumberedInOrder() {
		assertThat(clock.play().seq()).isEqualTo(1);
		assertThat(clock.seek(1_000).seq()).isEqualTo(2);
		assertThat(clock.pause().seq()).isEqualTo(3);
		assertThat(clock.state().seq()).isEqualTo(3);
	}

}

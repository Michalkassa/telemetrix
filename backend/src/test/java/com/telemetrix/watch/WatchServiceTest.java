package com.telemetrix.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemetrix.FakeTime;
import com.telemetrix.common.NotFoundException;
import com.telemetrix.watch.WatchService.Command;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WatchServiceTest {

	WatchService watches = new WatchService(new FakeTime(Instant.parse("2026-10-09T20:00:00Z")));

	@Test
	void eachWatchSessionHasItsOwnIdAndAHostKey() {
		var a = watches.create(5);
		var b = watches.create(5);

		assertThat(a.watchId()).isNotEqualTo(b.watchId());
		assertThat(a.hostKey()).isNotEqualTo(a.watchId()).hasSizeGreaterThanOrEqualTo(20);
		assertThat(watches.state(a.watchId()).sessionId()).isEqualTo(5);
	}

	@Test
	void theHostsCommandsChangeTheClock() {
		var created = watches.create(5);

		var state = watches.command(created.watchId(), new Command(created.hostKey(), "SEEK", 90_000.0));

		assertThat(state).hasValueSatisfying(s -> assertThat(s.positionMs()).isEqualTo(90_000));
	}

	@Test
	void anyoneElsesAreIgnored() {
		var created = watches.create(5);

		assertThat(watches.command(created.watchId(), new Command("guess", "PLAY", null))).isEmpty();
		assertThat(watches.command(created.watchId(), new Command(null, "PLAY", null))).isEmpty();
		assertThat(watches.state(created.watchId()).playing()).isFalse();
	}

	@Test
	void nonsenseIsIgnored() {
		var created = watches.create(5);

		assertThat(watches.command(created.watchId(), new Command(created.hostKey(), "REWIND", null))).isEmpty();
		assertThat(watches.command(created.watchId(), new Command(created.hostKey(), "SPEED", 100.0))).isEmpty();
		assertThat(watches.command(created.watchId(), new Command(created.hostKey(), "SEEK", null))).isEmpty();
		assertThat(watches.state(created.watchId()).seq()).isZero();
	}

	@Test
	void anUnknownWatchSessionIsNotFound() {
		assertThatThrownBy(() -> watches.state("nope")).isInstanceOf(NotFoundException.class);
		assertThat(watches.command("nope", new Command("k", "PLAY", null))).isEmpty();
	}

}

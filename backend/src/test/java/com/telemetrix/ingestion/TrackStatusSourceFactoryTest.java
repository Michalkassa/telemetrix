package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.telemetrix.Fixtures;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient.SessionStreams;
import com.telemetrix.racecontrol.RaceControlTrackStatus.Message;
import com.telemetrix.racecontrol.TrackStatusSource;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// Exact track status from the F1 archive when it's on and has the session; otherwise the same periods
// from OpenF1's race-control messages (docs/adr/0003). Singapore 2023's race: session 9165.
class TrackStatusSourceFactoryTest {

	static final List<Message> RACE_CONTROL = Fixtures.openF1RaceControl(9165).stream()
			.map(m -> new Message(m.date().toInstant(), m.message()))
			.toList();

	static final SessionStreams ARCHIVE = Fixtures.f1ArchiveSingapore2023Race();

	F1ArchiveClient archive = mock(F1ArchiveClient.class);

	@Test
	void usesTheF1ArchiveWhenEnabled() {
		given(archive.trackStatus(2023, 9165)).willReturn(Optional.of(ARCHIVE));

		var source = new TrackStatusSourceFactory(archive, true).create(2023, 9165, RACE_CONTROL);

		assertThat(source.kind()).isEqualTo(TrackStatusSource.Kind.F1_ARCHIVE);
		assertThat(source.periods().get(3).from()).hasToString("2023-09-17T12:36:40.792Z"); // the SC, to the ms
	}

	@Test
	void fallsBackToRaceControlWhenTheArchiveIsDisabled() {
		var source = new TrackStatusSourceFactory(archive, false).create(2023, 9165, RACE_CONTROL);

		assertThat(source.kind()).isEqualTo(TrackStatusSource.Kind.RACE_CONTROL);
		verify(archive, never()).trackStatus(anyInt(), anyInt());
	}

	@Test
	void fallsBackToRaceControlWhenTheArchiveHasNoFile() {
		given(archive.trackStatus(2023, 9165)).willReturn(Optional.empty());

		var source = new TrackStatusSourceFactory(archive, true).create(2023, 9165, RACE_CONTROL);

		assertThat(source.kind()).isEqualTo(TrackStatusSource.Kind.RACE_CONTROL);
		assertThat(source.periods()).isNotEmpty();
	}

	@Test
	void fallsBackToRaceControlWhenTheArchiveFails() {
		given(archive.trackStatus(2023, 9165)).willThrow(new ExternalApiException("F1 archive GET failed", null));

		var source = new TrackStatusSourceFactory(archive, true).create(2023, 9165, RACE_CONTROL);

		assertThat(source.kind()).isEqualTo(TrackStatusSource.Kind.RACE_CONTROL);
	}

	@Test
	void bothSourcesGiveTheSamePeriods() {
		given(archive.trackStatus(2023, 9165)).willReturn(Optional.of(ARCHIVE));
		List<TrackStatusPeriod> exact = new TrackStatusSourceFactory(archive, true).create(2023, 9165, RACE_CONTROL)
				.periods();
		List<TrackStatusPeriod> fromMessages = new TrackStatusSourceFactory(archive, false)
				.create(2023, 9165, RACE_CONTROL).periods();

		assertThat(fromMessages).hasSameSizeAs(exact);
		for (int i = 0; i < exact.size(); i++) {
			var a = exact.get(i);
			var b = fromMessages.get(i);
			assertThat(b.status()).as("period %d", i).isEqualTo(a.status());
			// race-control messages are timed to the second
			assertThat(Duration.between(a.from(), b.from()).abs()).as("start of period %d", i)
					.isLessThanOrEqualTo(Duration.ofSeconds(1));
			if (a.to() == null) {
				assertThat(b.to()).isNull();
			}
			else {
				assertThat(Duration.between(a.to(), b.to()).abs()).as("end of period %d", i)
						.isLessThanOrEqualTo(Duration.ofSeconds(1));
			}
		}
	}

}

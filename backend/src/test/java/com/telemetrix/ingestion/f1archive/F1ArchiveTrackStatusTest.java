package com.telemetrix.ingestion.f1archive;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.racecontrol.TrackStatus;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

// The F1 live-timing archive's TrackStatus stream for Singapore 2023's race. Its times are offsets
// from the start of the feed; SessionData pairs offsets with UTC, which anchors them (11:01:00.510).
class F1ArchiveTrackStatusTest {

	static Instant at(String time) {
		return Instant.parse("2023-09-17T" + time + "Z");
	}

	static String stream(String name) {
		try {
			return new ClassPathResource("fixtures/f1archive/singapore_2023_race_" + name + ".jsonStream")
					.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static final String TRACK_STATUS = stream("TrackStatus");

	static final String SESSION_DATA = stream("SessionData");

	@Test
	void anchorsTheFeedsOffsetsToUtc() {
		assertThat(F1ArchiveTrackStatus.feedStart(SESSION_DATA)).isEqualTo(at("11:01:00.510"));
	}

	@Test
	void statusCodesBecomeTimestampedPeriods() {
		var periods = F1ArchiveTrackStatus.periods(TRACK_STATUS, SESSION_DATA);

		assertThat(periods).contains(
				// 2 yellow -> 1 all clear
				new TrackStatusPeriod(TrackStatus.YELLOW, at("12:05:21.391"), at("12:05:26.579")),
				// 4 safety car
				new TrackStatusPeriod(TrackStatus.SAFETY_CAR, at("12:36:40.792"), at("12:42:49.803")),
				// 6 VSC deployed, 7 VSC ending
				new TrackStatusPeriod(TrackStatus.YELLOW, at("13:17:10.529"), at("13:18:06.044")),
				new TrackStatusPeriod(TrackStatus.VSC, at("13:18:06.044"), at("13:21:29.542")),
				new TrackStatusPeriod(TrackStatus.VSC_ENDING, at("13:21:29.542"), at("13:21:44.183")));
		// the race ended under a yellow that was never cleared
		assertThat(periods.getLast()).isEqualTo(new TrackStatusPeriod(TrackStatus.YELLOW, at("13:49:38.398"), null));
		assertThat(periods).hasSize(11);
	}

	@Test
	void aRedFlagIsCode5() {
		String feed = """
				00:00:00.000{"Status":"1","Message":"AllClear"}
				00:10:00.000{"Status":"5","Message":"Red"}
				00:40:00.000{"Status":"1","Message":"AllClear"}
				""";
		String sessionData = """
				00:00:05.000{"Series":{"0":{"Utc":"2024-01-01T12:00:05Z","Lap":1}}}
				""";

		assertThat(F1ArchiveTrackStatus.periods(feed, sessionData)).containsExactly(new TrackStatusPeriod(TrackStatus.RED,
				Instant.parse("2024-01-01T12:10:00Z"), Instant.parse("2024-01-01T12:40:00Z")));
	}

}

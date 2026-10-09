package com.telemetrix.ingestion;

import com.telemetrix.ingestion.f1archive.F1ArchiveClient;
import com.telemetrix.ingestion.f1archive.F1ArchiveClient.SessionStreams;
import com.telemetrix.ingestion.f1archive.F1ArchiveTrackStatus;
import com.telemetrix.racecontrol.RaceControlTrackStatus;
import com.telemetrix.racecontrol.TrackStatusSource;
import com.telemetrix.racecontrol.RaceControlTrackStatus.Message;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Factory pattern: picks a session's track-status source (docs/adr/0003). The F1 live-timing archive
// has the exact times; it's optional (f1archive.enabled) and unofficial, so when it's off, doesn't have
// the session, or fails, OpenF1's race-control messages give the same periods, to the second.
public class TrackStatusSourceFactory {

	private static final Logger log = LoggerFactory.getLogger(TrackStatusSourceFactory.class);

	private final F1ArchiveClient archive;

	private final boolean archiveEnabled;

	public TrackStatusSourceFactory(F1ArchiveClient archive, boolean archiveEnabled) {
		this.archive = archive;
		this.archiveEnabled = archiveEnabled;
	}

	/** `raceControl`: the session's race-control messages, in time order. */
	public TrackStatusSource create(int year, int sessionKey, List<Message> raceControl) {
		if (archiveEnabled) {
			try {
				Optional<SessionStreams> streams = archive.trackStatus(year, sessionKey);
				if (streams.isPresent()) {
					return source(TrackStatusSource.Kind.F1_ARCHIVE,
							F1ArchiveTrackStatus.periods(streams.get().trackStatus(), streams.get().sessionData()));
				}
				log.info("F1 archive has no track status for session {}; using race control", sessionKey);
			}
			catch (RuntimeException e) {
				log.warn("F1 archive track status for session {} failed ({}); using race control", sessionKey,
						e.getMessage());
			}
		}
		return source(TrackStatusSource.Kind.RACE_CONTROL, RaceControlTrackStatus.periods(raceControl));
	}

	private static TrackStatusSource source(TrackStatusSource.Kind kind, List<TrackStatusPeriod> periods) {
		return new TrackStatusSource() {

			@Override
			public Kind kind() {
				return kind;
			}

			@Override
			public List<TrackStatusPeriod> periods() {
				return periods;
			}

		};
	}

}

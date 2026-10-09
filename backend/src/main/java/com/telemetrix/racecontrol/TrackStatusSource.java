package com.telemetrix.racecontrol;

import java.util.List;

// Where a session's track-status periods come from (TrackStatusSourceFactory picks one).
public interface TrackStatusSource {

	enum Kind {
		F1_ARCHIVE, RACE_CONTROL
	}

	Kind kind();

	List<TrackStatusPeriod> periods();

}

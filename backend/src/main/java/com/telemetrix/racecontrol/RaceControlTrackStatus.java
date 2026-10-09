package com.telemetrix.racecontrol;

import com.telemetrix.racecontrol.RaceControlSignal.SectorFlag;
import com.telemetrix.racecontrol.RaceControlSignal.StatusChange;
import com.telemetrix.racecontrol.RaceControlSignal.TrackClear;
import com.telemetrix.racecontrol.TrackStatusTimeline.Change;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Track status from race-control messages alone (when the F1 archive isn't used): the safety car,
// the VSC and red flags last until the track is declared clear (or green); otherwise the track is
// yellow while any marshalling sector shows a yellow. Times are the messages' (to the second).
public final class RaceControlTrackStatus {

	public record Message(Instant at, String message) {
	}

	private RaceControlTrackStatus() {
	}

	/** `messages` in time order. */
	public static List<TrackStatusPeriod> periods(List<Message> messages) {
		Set<Integer> yellowSectors = new HashSet<>();
		TrackStatus neutralised = null; // SC, VSC, VSC ending or red: overrides any yellows
		List<Change> changes = new ArrayList<>();
		for (Message m : messages) {
			switch (RaceControlParser.parse(m.message())) {
				case SectorFlag flag when flag.yellow() -> yellowSectors.add(flag.sector());
				case SectorFlag flag -> yellowSectors.remove(flag.sector());
				case StatusChange change -> neutralised = change.status();
				case TrackClear clear -> {
					yellowSectors.clear();
					neutralised = null;
				}
				default -> {
					continue;
				}
			}
			changes.add(new Change(m.at(),
					neutralised != null ? neutralised : yellowSectors.isEmpty() ? null : TrackStatus.YELLOW));
		}
		return TrackStatusTimeline.periods(changes);
	}

}

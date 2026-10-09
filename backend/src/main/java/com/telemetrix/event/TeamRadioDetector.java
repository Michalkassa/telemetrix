package com.telemetrix.event;

import java.util.List;
import org.springframework.stereotype.Component;

// A marker for each team radio clip, linking to its recording.
@Component
class TeamRadioDetector implements EventDetector {

	@Override
	public List<RaceEvent> detect(RaceData race) {
		return race.radio().stream()
				.map(clip -> new RaceEvent(EventType.TEAM_RADIO, clip.at(), race.lapAt(clip.at()), null, null,
						Severity.LOW, List.of(clip.car()), "Team radio: " + race.code(clip.car()), clip.url()))
				.toList();
	}

}

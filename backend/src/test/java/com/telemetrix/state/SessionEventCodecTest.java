package com.telemetrix.state;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.event.EventType;
import com.telemetrix.event.RaceEvent;
import com.telemetrix.event.Severity;
import com.telemetrix.eventstore.NewEvent;
import com.telemetrix.eventstore.StoredEvent;
import com.telemetrix.eventstore.StreamId;
import com.telemetrix.eventstore.StreamType;
import com.telemetrix.racecontrol.PenaltyKind;
import com.telemetrix.state.SessionEvent.Detected;
import com.telemetrix.state.SessionEvent.DrsChanged;
import com.telemetrix.state.SessionEvent.FlagChanged;
import com.telemetrix.state.SessionEvent.PenaltyGiven;
import com.telemetrix.state.SessionEvent.PitIn;
import com.telemetrix.state.SessionEvent.PitOut;
import com.telemetrix.state.SessionEvent.SectorFlagged;
import com.telemetrix.state.SessionEvent.SessionRecorded;
import com.telemetrix.state.SessionEvent.TrackCleared;
import com.telemetrix.state.SessionEvent.TyresFitted;
import com.telemetrix.state.SessionEvent.WeatherReported;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

// Every session event survives the trip through the store's JSON unchanged, under a stable type name; a
// detected event is stored as the timeline's RaceEvent, for the timeline to read without this package.
class SessionEventCodecTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static final RaceEvent SC = new RaceEvent(EventType.SAFETY_CAR, t(250), 3, t(480), 5, Severity.HIGH, List.of(1, 44),
			"Safety car", null);

	static List<Object[]> events() {
		return List.of(
				new Object[] { new SessionRecorded(t(0), t(5000), 62, Map.of(1, "VER", 44, "HAM")), "SESSION_RECORDED" },
				new Object[] { new FlagChanged(t(10), Flag.SAFETY_CAR), "FLAG_CHANGED" },
				new Object[] { new PitIn(t(20), 14), "PIT_IN" },
				new Object[] { new PitOut(t(45), 14), "PIT_OUT" },
				new Object[] { new TyresFitted(null, 1, "MEDIUM", 1, 0), "TYRES_FITTED" },
				new Object[] { new TyresFitted(t(45), 14, "HARD", 21, 3), "TYRES_FITTED" },
				new Object[] { new PenaltyGiven(t(60), 14, PenaltyKind.TIME, 5), "PENALTY_GIVEN" },
				new Object[] { new PenaltyGiven(t(61), 14, PenaltyKind.DRIVE_THROUGH, null), "PENALTY_GIVEN" },
				new Object[] { new SectorFlagged(t(70), 7, SectorFlag.DOUBLE_YELLOW), "SECTOR_FLAGGED" },
				new Object[] { new SectorFlagged(t(80), 7, null), "SECTOR_FLAGGED" },
				new Object[] { new TrackCleared(t(90)), "TRACK_CLEARED" },
				new Object[] { new DrsChanged(t(100), true), "DRS_CHANGED" },
				new Object[] { new WeatherReported(t(110), 30.0, 38.3, 70.5, false, null), "WEATHER_REPORTED" },
				new Object[] { new Detected(SC), "RACE_EVENT_DETECTED" });
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("events")
	void roundTripsUnderItsTypeName(SessionEvent event, String type) {
		NewEvent stored = SessionEventCodec.encode(event);

		assertThat(stored.type()).isEqualTo(type);
		assertThat(stored.occurredAt()).isEqualTo(event.at());
		assertThat(SessionEventCodec.decode(asStored(stored))).isEqualTo(event);
	}

	@org.junit.jupiter.api.Test
	void aDetectedEventIsStoredAsTheTimelinesRaceEvent() {
		var stored = asStored(SessionEventCodec.encode(new Detected(SC)));

		assertThat(JSON.treeToValue(stored.payload(), RaceEvent.class)).isEqualTo(SC);
		assertThat(stored.type()).isEqualTo(RaceEvent.STORED_TYPE);
	}

	// as the store hands it back: the payload through JSON text
	static StoredEvent asStored(NewEvent event) {
		return new StoredEvent(1, new StreamId(StreamType.SESSION, 1), 1, 1, event.type(), event.occurredAt(),
				JSON.readTree(JSON.writeValueAsString(event.payload())), t(0));
	}

}

package com.telemetrix.state;

import com.telemetrix.event.RaceEvent;
import com.telemetrix.eventstore.NewEvent;
import com.telemetrix.eventstore.StoredEvent;
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
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

// Session events to and from the store: a stable type name each (stored data outlives class names) and the
// record as its JSON payload. A detected event's payload is the timeline's RaceEvent itself.
public final class SessionEventCodec {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private record Kind(String type, Class<? extends SessionEvent> eventClass) {
	}

	private static final List<Kind> KINDS = List.of(new Kind("SESSION_RECORDED", SessionRecorded.class),
			new Kind("FLAG_CHANGED", FlagChanged.class), new Kind("PIT_IN", PitIn.class),
			new Kind("PIT_OUT", PitOut.class), new Kind("TYRES_FITTED", TyresFitted.class),
			new Kind("PENALTY_GIVEN", PenaltyGiven.class), new Kind("SECTOR_FLAGGED", SectorFlagged.class),
			new Kind("TRACK_CLEARED", TrackCleared.class), new Kind("DRS_CHANGED", DrsChanged.class),
			new Kind("WEATHER_REPORTED", WeatherReported.class), new Kind(RaceEvent.STORED_TYPE, Detected.class));

	private static final Map<Class<?>, String> NAMES = KINDS.stream().collect(Collectors.toMap(Kind::eventClass, Kind::type));

	private static final Map<String, Class<? extends SessionEvent>> CLASSES = KINDS.stream()
			.collect(Collectors.toMap(Kind::type, Kind::eventClass, (a, b) -> a, java.util.HashMap::new));

	private SessionEventCodec() {
	}

	public static NewEvent encode(SessionEvent event) {
		Object payload = event instanceof Detected d ? d.event() : event;
		return new NewEvent(NAMES.get(event.getClass()), event.at(), payload);
	}

	public static SessionEvent decode(StoredEvent event) {
		Class<? extends SessionEvent> type = CLASSES.get(event.type());
		if (type == null) {
			throw new IllegalStateException("Unknown session event type " + event.type() + " in " + event.stream());
		}
		if (type == Detected.class) {
			return new Detected(JSON.treeToValue(event.payload(), RaceEvent.class));
		}
		return JSON.treeToValue(event.payload(), type);
	}

	public static List<SessionEvent> decode(List<StoredEvent> events) {
		return events.stream().map(SessionEventCodec::decode).toList();
	}


}

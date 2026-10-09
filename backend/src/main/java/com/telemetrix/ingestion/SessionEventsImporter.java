package com.telemetrix.ingestion;

import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.ingestion.openf1.OpenF1RaceControl;
import com.telemetrix.ingestion.openf1.OpenF1TeamRadio;
import com.telemetrix.ingestion.openf1.OpenF1Weather;
import com.telemetrix.race.Session;
import com.telemetrix.racecontrol.RaceControlParser;
import com.telemetrix.racecontrol.RaceControlSignal;
import com.telemetrix.racecontrol.RaceControlTrackStatus;
import com.telemetrix.racecontrol.RaceControlMessage;
import com.telemetrix.racecontrol.RaceControlMessageRepository;
import com.telemetrix.racecontrol.SessionPenalty;
import com.telemetrix.racecontrol.SessionPenaltyRepository;
import com.telemetrix.racecontrol.SessionTrackStatus;
import com.telemetrix.racecontrol.SessionTrackStatusRepository;
import com.telemetrix.racecontrol.TeamRadioClip;
import com.telemetrix.racecontrol.TeamRadioClipRepository;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import com.telemetrix.racecontrol.TrackStatusSource;
import com.telemetrix.racecontrol.WeatherReading;
import com.telemetrix.racecontrol.WeatherReadingRepository;
import com.telemetrix.timing.Lap;
import com.telemetrix.timing.LapRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// Downloads what happened in one session besides lap times: weather, race-control messages (and the
// penalties among them) and team radio — 3 OpenF1 requests, each for the whole session — and works out
// the track-status periods (TrackStatusSourceFactory: the F1 archive or race control). Replaces whatever
// the session had before. Runs after the session's laps are stored: they give each period its laps.
@Component
class SessionEventsImporter {

	private final OpenF1Client openF1;

	private final TrackStatusSourceFactory trackStatusSources;

	private final LapRepository laps;

	private final WeatherReadingRepository weather;

	private final RaceControlMessageRepository events;

	private final SessionPenaltyRepository penalties;

	private final SessionTrackStatusRepository trackStatus;

	private final TeamRadioClipRepository radio;

	SessionEventsImporter(OpenF1Client openF1, TrackStatusSourceFactory trackStatusSources, LapRepository laps,
			WeatherReadingRepository weather, RaceControlMessageRepository events, SessionPenaltyRepository penalties,
			SessionTrackStatusRepository trackStatus, TeamRadioClipRepository radio) {
		this.openF1 = openF1;
		this.trackStatusSources = trackStatusSources;
		this.laps = laps;
		this.weather = weather;
		this.events = events;
		this.penalties = penalties;
		this.trackStatus = trackStatus;
		this.radio = radio;
	}

	void importSession(Session session, int year, ImportReport.Builder report) {
		int key = session.getOpenf1SessionKey();
		List<OpenF1RaceControl> messages = openF1.raceControl(key).stream()
				.filter(m -> m.date() != null && m.message() != null)
				.sorted(Comparator.comparing(OpenF1RaceControl::date)) // stable: same-second messages keep their order
				.toList();
		List<OpenF1Weather> readings = openF1.weather(key);
		List<OpenF1TeamRadio> clips = openF1.teamRadio(key);

		penalties.deleteBySession(session); // first: they point at race events
		events.deleteBySession(session);
		trackStatus.deleteBySession(session);
		weather.deleteBySession(session);
		radio.deleteBySession(session);

		for (OpenF1Weather w : readings) {
			if (w.date() == null) {
				continue;
			}
			weather.save(new WeatherReading(session, w.date().toInstant(), w.airTemperature(), w.trackTemperature(),
					w.humidity(), w.pressure(), w.rainfall() == null ? null : w.rainfall() > 0, w.windSpeed(),
					w.windDirection()));
			report.inserted("weather");
		}

		for (OpenF1RaceControl m : messages) {
			RaceControlMessage event = events.save(new RaceControlMessage(session, m.date().toInstant(), m.lapNumber(), m.category(),
					m.flag(), m.scope(), m.sector(), m.driverNumber(), m.message()));
			report.inserted("raceEvents");
			if (RaceControlParser.parse(m.message()) instanceof RaceControlSignal.Penalty p) {
				penalties.save(new SessionPenalty(session, event, p.driverNumber(), p.kind(), p.amount(), p.reason()));
				report.inserted("penalties");
			}
		}

		TrackStatusSource source = trackStatusSources.create(year, key, messages.stream()
				.map(m -> new RaceControlTrackStatus.Message(m.date().toInstant(), m.message()))
				.toList());
		NavigableMap<Instant, Integer> lapStarts = leaderLapStarts(session);
		for (TrackStatusPeriod period : source.periods()) {
			trackStatus.save(new SessionTrackStatus(session, period, lapAt(lapStarts, period.from()),
					period.to() == null ? null : lapAt(lapStarts, period.to()), source.kind()));
			report.inserted("trackStatus");
		}
		report.note("session " + key + " (" + session.getName() + "): track status from " + source.kind());

		for (OpenF1TeamRadio clip : clips) {
			if (clip.date() == null || clip.recordingUrl() == null) {
				continue;
			}
			radio.save(new TeamRadioClip(session, clip.driverNumber(), clip.date().toInstant(), clip.recordingUrl()));
			report.inserted("teamRadio");
		}
	}

	// when the leader started each lap: the first car to start it
	private NavigableMap<Instant, Integer> leaderLapStarts(Session session) {
		Map<Integer, Instant> firstStart = laps.findBySessionOrderByDriverNumberAscLapNumberAsc(session).stream()
				.filter(l -> l.getStartsAt() != null)
				.collect(Collectors.toMap(Lap::getLapNumber, Lap::getStartsAt,
						(a, b) -> a.isBefore(b) ? a : b));
		NavigableMap<Instant, Integer> byTime = new TreeMap<>();
		firstStart.forEach((lap, start) -> byTime.merge(start, lap, Math::max));
		return byTime;
	}

	// the leader's lap at a moment; null before the start (or without laps)
	private static Integer lapAt(NavigableMap<Instant, Integer> lapStarts, Instant at) {
		var entry = lapStarts.floorEntry(at);
		return entry == null ? null : entry.getValue();
	}

}

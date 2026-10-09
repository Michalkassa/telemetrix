package com.telemetrix.event;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.event.RaceData.LapTime;
import com.telemetrix.event.RaceData.Message;
import com.telemetrix.event.RaceData.PenaltyIssued;
import com.telemetrix.event.RaceData.RadioClip;
import com.telemetrix.event.RaceData.Stint;
import com.telemetrix.event.RaceData.Weather;
import com.telemetrix.race.Session;
import com.telemetrix.race.SessionRepository;
import com.telemetrix.racecontrol.RaceControlMessageRepository;
import com.telemetrix.racecontrol.SessionPenaltyRepository;
import com.telemetrix.racecontrol.SessionTrackStatusRepository;
import com.telemetrix.racecontrol.TeamRadioClipRepository;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import com.telemetrix.racecontrol.WeatherReadingRepository;
import com.telemetrix.replay.RaceReplay;
import com.telemetrix.replay.ReplayService;
import com.telemetrix.timing.LapRepository;
import com.telemetrix.timing.TireStintRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Loads everything the detectors need for one session: the replay (each car's progress and pit windows)
// and the session's stored laps, stints, track status, race control, penalties, team radio and weather.
@Service
@Transactional(readOnly = true)
public class RaceDataLoader {

	private final ReplayService replays;

	private final SessionRepository sessions;

	private final LapRepository laps;

	private final TireStintRepository stints;

	private final SessionTrackStatusRepository trackStatus;

	private final RaceControlMessageRepository raceControl;

	private final SessionPenaltyRepository penalties;

	private final TeamRadioClipRepository radio;

	private final WeatherReadingRepository weather;

	RaceDataLoader(ReplayService replays, SessionRepository sessions, LapRepository laps, TireStintRepository stints,
			SessionTrackStatusRepository trackStatus, RaceControlMessageRepository raceControl,
			SessionPenaltyRepository penalties, TeamRadioClipRepository radio, WeatherReadingRepository weather) {
		this.replays = replays;
		this.sessions = sessions;
		this.laps = laps;
		this.stints = stints;
		this.trackStatus = trackStatus;
		this.raceControl = raceControl;
		this.penalties = penalties;
		this.radio = radio;
		this.weather = weather;
	}

	public RaceData load(long sessionId) {
		RaceReplay replay = replays.replay(sessionId); // 404 without timing
		Session session = sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("Session", sessionId));
		Map<Integer, String> codes = new LinkedHashMap<>();
		replay.drivers().forEach(d -> codes.put(d.number(), d.code()));
		return new RaceData(replay.clockStart(), replay.chequeredAt(), replay.totalLaps(), codes, replay.timelines(),
				replay.pits(),
				laps.findBySessionOrderByDriverNumberAscLapNumberAsc(session).stream()
						.map(l -> new LapTime(l.getDriverNumber(), l.getLapNumber(), l.getStartsAt(), l.getLapMs()))
						.toList(),
				stints.findBySessionOrderByDriverNumberAsc(session).stream()
						.filter(s -> s.getLapStart() != null)
						.map(s -> new Stint(s.getDriverNumber(), s.getStintNumber(), s.getCompound(), s.getLapStart(),
								s.getTyreAgeAtStart() == null ? 0 : s.getTyreAgeAtStart()))
						.toList(),
				trackStatus.findBySessionOrderByStartsAtAsc(session).stream()
						.map(p -> new TrackStatusPeriod(p.getStatus(), p.getStartsAt(), p.getEndsAt()))
						.toList(),
				raceControl.findBySessionOrderByOccurredAtAscIdAsc(session).stream()
						.map(m -> new Message(m.getOccurredAt(),
								m.getLapNumber() == null ? null : m.getLapNumber().intValue(), m.getMessage()))
						.toList(),
				penalties.findBySessionOrderByIssuedAtAsc(session).stream()
						.map(p -> new PenaltyIssued(p.getDriverNumber(), p.getKind(),
								p.getAmount() == null ? null : p.getAmount().intValue(), p.getReason(), p.getIssuedAt(),
								p.getLapNumber() == null ? null : p.getLapNumber().intValue()))
						.toList(),
				radio.findBySessionOrderByRecordedAtAsc(session).stream()
						.map(c -> new RadioClip(c.getDriverNumber(), c.getRecordedAt(), c.getRecordingUrl()))
						.toList(),
				weather.findBySessionOrderByRecordedAtAsc(session).stream()
						.map(w -> new Weather(w.getRecordedAt(), w.isRainfall()))
						.toList());
	}

}

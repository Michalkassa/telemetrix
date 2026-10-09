package com.telemetrix.ingestion;

import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.ingestion.openf1.OpenF1Lap;
import com.telemetrix.ingestion.openf1.OpenF1PitStop;
import com.telemetrix.ingestion.openf1.OpenF1Stint;
import com.telemetrix.race.Session;
import com.telemetrix.timing.Lap;
import com.telemetrix.timing.LapRepository;
import com.telemetrix.timing.SessionPitStop;
import com.telemetrix.timing.SessionPitStopRepository;
import com.telemetrix.timing.TireStint;
import com.telemetrix.timing.TireStintRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

// Downloads one session's laps, tyre stints and pit stops from OpenF1 — 3 requests, each for all
// cars — checks them, and stores them, replacing whatever the session had before.
@Component
class SessionTimingImporter {

	private final OpenF1Client openF1;

	private final LapConsistencyValidator validator;

	private final LapRepository laps;

	private final TireStintRepository stints;

	private final SessionPitStopRepository pitStops;

	SessionTimingImporter(OpenF1Client openF1, LapConsistencyValidator validator, LapRepository laps,
			TireStintRepository stints, SessionPitStopRepository pitStops) {
		this.openF1 = openF1;
		this.validator = validator;
		this.laps = laps;
		this.stints = stints;
		this.pitStops = pitStops;
	}

	void importSession(Session session, ImportReport.Builder report) {
		int key = session.getOpenf1SessionKey();
		List<OpenF1Lap> sourceLaps = openF1.laps(key);
		List<OpenF1Stint> sourceStints = openF1.stints(key);
		List<OpenF1PitStop> sourcePits = openF1.pitStops(key);
		validator.check(sourceLaps, sourceStints, sourcePits)
				.forEach(f -> report.note(f.severity() + " session " + key + " (" + session.getName() + "): " + f.message()));

		laps.deleteBySession(session);
		stints.deleteBySession(session);
		pitStops.deleteBySession(session);

		// one row per car and lap (or stint): a repeated row in the source keeps its last version
		Map<String, OpenF1Lap> uniqueLaps = new LinkedHashMap<>();
		sourceLaps.forEach(l -> uniqueLaps.put(l.driverNumber() + "/" + l.lapNumber(), l));
		for (OpenF1Lap source : uniqueLaps.values()) {
			var lap = new Lap(session, source.driverNumber(), source.lapNumber(),
					source.dateStart() == null ? null : source.dateStart().toInstant());
			lap.setTimes(millis(source.durationSector1()), millis(source.durationSector2()),
					millis(source.durationSector3()), millis(source.lapDuration()));
			lap.setSpeeds(shortOrNull(source.i1Speed()), shortOrNull(source.i2Speed()), shortOrNull(source.stSpeed()));
			lap.setPitOutLap(Boolean.TRUE.equals(source.isPitOutLap()));
			laps.save(lap);
			report.inserted("laps");
		}

		Map<String, OpenF1Stint> uniqueStints = new LinkedHashMap<>();
		sourceStints.forEach(s -> uniqueStints.put(s.driverNumber() + "/" + s.stintNumber(), s));
		for (OpenF1Stint source : uniqueStints.values()) {
			stints.save(new TireStint(session, source.driverNumber(), source.stintNumber(), source.compound(),
					source.lapStart(), source.lapEnd(), source.tyreAgeAtStart()));
			report.inserted("stints");
		}

		for (OpenF1PitStop source : sourcePits) {
			pitStops.save(new SessionPitStop(session, source.driverNumber(), source.lapNumber(),
					source.date() == null ? null : source.date().toInstant(), millis(source.timeInLane()),
					millis(source.stopDuration())));
			report.inserted("pitStops");
		}
	}

	private static Integer millis(Double seconds) {
		return seconds == null ? null : (int) Math.round(seconds * 1000);
	}

	private static Short shortOrNull(Integer value) {
		return value == null ? null : value.shortValue();
	}

}

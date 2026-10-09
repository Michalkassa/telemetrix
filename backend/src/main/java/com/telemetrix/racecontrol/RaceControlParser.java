package com.telemetrix.racecontrol;

import com.telemetrix.racecontrol.RaceControlSignal.Chequered;
import com.telemetrix.racecontrol.RaceControlSignal.Incident;
import com.telemetrix.racecontrol.RaceControlSignal.Other;
import com.telemetrix.racecontrol.RaceControlSignal.Penalty;
import com.telemetrix.racecontrol.RaceControlSignal.SafetyCarInThisLap;
import com.telemetrix.racecontrol.RaceControlSignal.SectorFlag;
import com.telemetrix.racecontrol.RaceControlSignal.StatusChange;
import com.telemetrix.racecontrol.RaceControlSignal.TrackClear;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads a race-control message (OpenF1 /race_control `message`) into what it means for the race: track
// status changes, sector flags, penalties and incidents. Anything else is Other.
public final class RaceControlParser {

	// "(DOUBLE) YELLOW IN TRACK SECTOR 13", "CLEAR IN TRACK SECTOR 7"
	private static final Pattern SECTOR_FLAG = Pattern.compile("^(DOUBLE YELLOW|YELLOW|CLEAR) IN TRACK SECTOR (\\d+)$");

	// "FIA STEWARDS: 5 SECOND TIME PENALTY FOR CAR 14 (ALO) - REASON", "... DRIVE THROUGH PENALTY ...",
	// "... 10 SECOND STOP/GO PENALTY ...", "... 3 PLACE GRID PENALTY ..."
	private static final Pattern PENALTY = Pattern.compile(
			"^FIA STEWARDS: (?:(\\d+) SECOND )?(?:(\\d+) PLACE )?(TIME|DRIVE THROUGH|STOP/GO|GRID) PENALTY FOR CAR (\\d+)\\b.*? - (.+)$");

	// "[TURN 13 |PIT EXIT ]INCIDENT INVOLVING CARS 11 (PER) AND 23 (ALB) NOTED - CAUSING A COLLISION"
	private static final Pattern INCIDENT = Pattern.compile(
			"^(?:FIA STEWARDS: )?.*?INCIDENT INVOLVING CARS? (.+?) (NOTED|UNDER INVESTIGATION|REVIEWED NO FURTHER INVESTIGATION|WILL BE INVESTIGATED AFTER THE RACE|NO FURTHER ACTION|NO INVESTIGATION NECESSARY) - (.+)$");

	// "CAR 16 (LEC) STOPPED AT TURN 8"
	private static final Pattern CAR_STOPPED = Pattern.compile("^CAR (\\d+) \\([A-Z]+\\) STOPPED\\b.*$");

	private static final Pattern CAR_NUMBER = Pattern.compile("(\\d+) \\(");

	private RaceControlParser() {
	}

	public static RaceControlSignal parse(String message) {
		String m = message == null ? "" : message.trim();
		switch (m) {
			case "SAFETY CAR DEPLOYED":
				return new StatusChange(TrackStatus.SAFETY_CAR);
			case "SAFETY CAR IN THIS LAP":
				return new SafetyCarInThisLap();
			case "VIRTUAL SAFETY CAR DEPLOYED":
				return new StatusChange(TrackStatus.VSC);
			case "VIRTUAL SAFETY CAR ENDING":
				return new StatusChange(TrackStatus.VSC_ENDING);
			case "RED FLAG":
				return new StatusChange(TrackStatus.RED);
			case "TRACK CLEAR", "GREEN LIGHT - PIT EXIT OPEN":
				return new TrackClear();
			case "CHEQUERED FLAG":
				return new Chequered();
			default:
				break;
		}
		Matcher flag = SECTOR_FLAG.matcher(m);
		if (flag.matches()) {
			return new SectorFlag(Integer.parseInt(flag.group(2)), !flag.group(1).equals("CLEAR"));
		}
		Matcher penalty = PENALTY.matcher(m);
		if (penalty.matches()) { // a "PENALTY SERVED - ..." message doesn't match: it isn't a new penalty
			String amount = penalty.group(1) != null ? penalty.group(1) : penalty.group(2);
			return new Penalty(Integer.parseInt(penalty.group(4)), kind(penalty.group(3)),
					amount == null ? null : Integer.valueOf(amount), penalty.group(5));
		}
		Matcher incident = INCIDENT.matcher(m);
		if (incident.matches()) {
			return new Incident(cars(incident.group(1)), incident.group(2), incident.group(3));
		}
		Matcher stopped = CAR_STOPPED.matcher(m);
		if (stopped.matches()) {
			return new RaceControlSignal.CarStopped(Integer.parseInt(stopped.group(1)));
		}
		return new Other();
	}

	private static PenaltyKind kind(String text) {
		return switch (text) {
			case "TIME" -> PenaltyKind.TIME;
			case "DRIVE THROUGH" -> PenaltyKind.DRIVE_THROUGH;
			case "STOP/GO" -> PenaltyKind.STOP_GO;
			default -> PenaltyKind.GRID;
		};
	}

	// "11 (PER) AND 23 (ALB)" -> [11, 23]
	private static List<Integer> cars(String text) {
		return CAR_NUMBER.matcher(text).results().map(r -> Integer.valueOf(r.group(1))).toList();
	}

}

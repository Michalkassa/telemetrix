package com.telemetrix.racecontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.racecontrol.RaceControlSignal.Chequered;
import com.telemetrix.racecontrol.RaceControlSignal.Incident;
import com.telemetrix.racecontrol.RaceControlSignal.Other;
import com.telemetrix.racecontrol.RaceControlSignal.Penalty;
import com.telemetrix.racecontrol.RaceControlSignal.SafetyCarInThisLap;
import com.telemetrix.racecontrol.RaceControlSignal.SectorFlag;
import com.telemetrix.racecontrol.RaceControlSignal.StatusChange;
import com.telemetrix.racecontrol.RaceControlSignal.TrackClear;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// Real race-control messages (OpenF1 /race_control, mostly Singapore 2023) and what they mean.
class RaceControlParserTest {

	static Stream<Arguments> messages() {
		return Stream.of(
				// the safety car and the virtual safety car
				Arguments.of("SAFETY CAR DEPLOYED", new StatusChange(TrackStatus.SAFETY_CAR)),
				Arguments.of("SAFETY CAR IN THIS LAP", new SafetyCarInThisLap()),
				Arguments.of("VIRTUAL SAFETY CAR DEPLOYED", new StatusChange(TrackStatus.VSC)),
				Arguments.of("VIRTUAL SAFETY CAR ENDING", new StatusChange(TrackStatus.VSC_ENDING)),
				Arguments.of("RED FLAG", new StatusChange(TrackStatus.RED)),
				// flags
				Arguments.of("YELLOW IN TRACK SECTOR 13", new SectorFlag(13, true)),
				Arguments.of("DOUBLE YELLOW IN TRACK SECTOR 7", new SectorFlag(7, true)),
				Arguments.of("CLEAR IN TRACK SECTOR 7", new SectorFlag(7, false)),
				Arguments.of("TRACK CLEAR", new TrackClear()),
				Arguments.of("GREEN LIGHT - PIT EXIT OPEN", new TrackClear()),
				Arguments.of("CHEQUERED FLAG", new Chequered()),
				// penalties
				Arguments.of(
						"FIA STEWARDS: 5 SECOND TIME PENALTY FOR CAR 14 (ALO) - FAILING TO FOLLOW RACE DIRECTORS INSTRUCTIONS – CROSSING THE LINE AT PIT ENTRY",
						new Penalty(14, PenaltyKind.TIME, 5,
								"FAILING TO FOLLOW RACE DIRECTORS INSTRUCTIONS – CROSSING THE LINE AT PIT ENTRY")),
				Arguments.of("FIA STEWARDS: DRIVE THROUGH PENALTY FOR CAR 24 (ZHO) - SPEEDING IN THE PIT LANE",
						new Penalty(24, PenaltyKind.DRIVE_THROUGH, null, "SPEEDING IN THE PIT LANE")),
				Arguments.of("FIA STEWARDS: 10 SECOND STOP/GO PENALTY FOR CAR 20 (MAG) - UNSAFE RELEASE",
						new Penalty(20, PenaltyKind.STOP_GO, 10, "UNSAFE RELEASE")),
				Arguments.of("FIA STEWARDS: 3 PLACE GRID PENALTY FOR CAR 2 (SAR) - CAUSING A COLLISION",
						new Penalty(2, PenaltyKind.GRID, 3, "CAUSING A COLLISION")),
				// a penalty being served is not a new one
				Arguments.of("FIA STEWARDS: PENALTY SERVED - 5 SECOND TIME PENALTY FOR CAR 14 (ALO) - CROSSING THE LINE AT PIT ENTRY",
						new Other()),
				// incidents
				Arguments.of("TURN 13 INCIDENT INVOLVING CARS 11 (PER) AND 23 (ALB) NOTED - CAUSING A COLLISION",
						new Incident(List.of(11, 23), "NOTED", "CAUSING A COLLISION")),
				Arguments.of(
						"FIA STEWARDS: INCIDENT INVOLVING CAR 14 (ALO) UNDER INVESTIGATION - FAILING TO FOLLOW RACE DIRECTORS INSTRUCTIONS – CROSSING THE LINE AT PIT ENTRY",
						new Incident(List.of(14), "UNDER INVESTIGATION",
								"FAILING TO FOLLOW RACE DIRECTORS INSTRUCTIONS – CROSSING THE LINE AT PIT ENTRY")),
				Arguments.of(
						"FIA STEWARDS: TURN 2 INCIDENT INVOLVING CAR 44 (HAM) REVIEWED NO FURTHER INVESTIGATION - LEAVING THE TRACK AND GAINING AN ADVANTAGE",
						new Incident(List.of(44), "REVIEWED NO FURTHER INVESTIGATION",
								"LEAVING THE TRACK AND GAINING AN ADVANTAGE")),
				Arguments.of(
						"FIA STEWARDS: PIT EXIT INCIDENT INVOLVING CARS 11 (PER) AND 23 (ALB) WILL BE INVESTIGATED AFTER THE RACE - VSC INFRINGEMENT",
						new Incident(List.of(11, 23), "WILL BE INVESTIGATED AFTER THE RACE", "VSC INFRINGEMENT")),
				// a car stopped on track
				Arguments.of("CAR 16 (LEC) STOPPED AT TURN 8", new RaceControlSignal.CarStopped(16)),
				// everything else
				Arguments.of("CAR 14 (ALO) OFF TRACK AND CONTINUED AT TURN 14", new Other()),
				Arguments.of("DRS ENABLED", new Other()),
				Arguments.of("CAR 10 (GAS) TIME 1:41.829 DELETED - TRACK LIMITS AT TURN 7 LAP 6 20:12:55", new Other()),
				Arguments.of("RISK OF RAIN FOR THIS SESSION IS 10%", new Other()));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("messages")
	void understandsTheMessage(String message, RaceControlSignal expected) {
		assertThat(RaceControlParser.parse(message)).isEqualTo(expected);
	}

}

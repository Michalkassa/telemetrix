package com.telemetrix.state;

/**
 * One car at a moment. Gaps are seconds — to the leader and to the car ahead — measured where the car is
 * now: how long ago the other car was at the same point (null for the leader's interval, and for cars on
 * the grid or retired). `lapsDown`: whole laps behind the leader. `tyreAge`: laps on the current tyres,
 * counting laps they'd done before being fitted. `penaltySeconds`: time penalties so far (added after
 * the race); `pendingPenalties`: drive-throughs and stop-gos not yet served.
 */
public record CarState(int number, String code, int position, int lap, double lapsDone, CarStatus status,
		Double gapToLeader, Double interval, int lapsDown, String compound, Integer tyreAge, int pitStops,
		int penaltySeconds, int pendingPenalties, boolean drs, boolean mandatoryStopDone) {
}

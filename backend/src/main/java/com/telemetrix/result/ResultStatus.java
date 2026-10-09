package com.telemetrix.result;

// How an entry's session ended. The reason in words ("Accident", "Engine", "+1 Lap") is kept
// separately as the status detail.
public enum ResultStatus {

	FINISHED, LAPPED, RETIRED_INCIDENT, RETIRED_MECHANICAL, DISQUALIFIED, DID_NOT_START, DID_NOT_QUALIFY, OTHER;

	/** Jolpica's integer status code; null when there is none (practice and qualifying). */
	public static ResultStatus fromJolpicaCode(String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		return switch (code.strip()) {
			case "0" -> FINISHED;
			case "1" -> LAPPED;
			case "10" -> RETIRED_INCIDENT; // accident, collision, spun off
			case "11" -> RETIRED_MECHANICAL; // engine, gearbox, ...
			case "20" -> DISQUALIFIED;
			case "30" -> DID_NOT_START;
			case "40" -> DID_NOT_QUALIFY;
			default -> OTHER;
		};
	}

}

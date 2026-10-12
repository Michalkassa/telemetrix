package com.telemetrix.common;

// What text in a request may look like (docs/adr/0007): checked before anything reaches a query, so a
// hostile value is a 400 and is never echoed in an error.
public final class Inputs {

	/** A driver, constructor or circuit reference as the history has them: "max_verstappen", "red_bull". */
	public static final String REF = "[A-Za-z0-9_.-]{1,50}";

	/** A watch-together id (base64url). */
	public static final String WATCH_ID = "[A-Za-z0-9_-]{1,32}";

	private Inputs() {
	}

}

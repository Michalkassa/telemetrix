package com.telemetrix.ingestion.openf1;

// OpenF1 could not answer a request: a rejected request, or errors that persisted after retrying.
public class OpenF1Exception extends RuntimeException {

	public OpenF1Exception(String message, Throwable cause) {
		super(message, cause);
	}

}

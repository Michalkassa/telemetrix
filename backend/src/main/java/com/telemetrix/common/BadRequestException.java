package com.telemetrix.common;

// The request can't be answered as asked (e.g. comparing a circuit in a year it had no race); maps to 400.
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}

}

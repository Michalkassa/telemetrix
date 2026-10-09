package com.telemetrix.common;

// A service we depend on (an external API) failed, so we can't answer: HTTP 502 Bad Gateway.
public class UpstreamServiceException extends RuntimeException {

	public UpstreamServiceException(String message, Throwable cause) {
		super(message, cause);
	}

}

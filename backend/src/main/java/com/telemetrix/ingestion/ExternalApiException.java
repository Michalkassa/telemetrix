package com.telemetrix.ingestion;

import com.telemetrix.common.UpstreamServiceException;

// An external data source (OpenF1, Jolpica, ...) could not answer a request: it was rejected, or
// errors persisted after retrying. The message names the source and the request.
public class ExternalApiException extends UpstreamServiceException {

	public ExternalApiException(String message, Throwable cause) {
		super(message, cause);
	}

}

package com.telemetrix.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Turns exceptions from any controller into RFC 9457 problem details, in one place.
// Extending ResponseEntityExceptionHandler keeps Spring's own errors (bad JSON, 405, ...) in the
// same format.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(BadRequestException.class)
	ProblemDetail handleBadRequest(BadRequestException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	// An external API we depend on failed. Its request and status are useful to the caller and aren't
	// internals of ours, so they go in the detail.
	@ExceptionHandler(UpstreamServiceException.class)
	ProblemDetail handleUpstream(UpstreamServiceException e) {
		log.warn("Upstream service failed: {}", e.getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
	}

	// Anything else is a bug: log it in full, but never send internals (SQL, class names) to the client.
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception e) {
		log.error("Unhandled exception", e);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
	}

	// A value of the wrong type (?round=abc): say which parameter, never echo the value (docs/adr/0007).
	@Override
	protected @Nullable ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String name = e instanceof MethodArgumentTypeMismatchException m ? m.getName() : e.getPropertyName();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid value for '" + name + "'");
		return handleExceptionInternal(e, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	// @Valid failures: add an "errors" map of field -> message so clients can show them per field.
	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		ProblemDetail problem = e.getBody();
		problem.setProperty("errors", errors);
		return handleExceptionInternal(e, problem, headers, status, request);
	}

}

// TEMPLATE 09c — Central error handling
// Where it goes: src/main/java/com/telemetrix/common/GlobalExceptionHandler.java
//
// Without this, an exception from a service becomes a 500. With it, you map each exception type to
// the right status code in one place, for every controller.
//
// The body is a ProblemDetail — the standard JSON error format (RFC 9457):
//   {"type":"about:blank","title":"Not Found","status":404,"detail":"Book 42 not found",
//    "instance":"/api/books/42"}
// (application.properties: spring.mvc.problemdetails.enabled=true gives Spring's own errors,
//  like validation failures, the same format.)

package com.telemetrix.library;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // applies to all @RestControllers
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // "When any controller throws NotFoundException, call this method and send what it returns."
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // Safety net for everything else = a bug. Log the full stack trace for yourself, but send the
    // client a generic message: never leak internals (SQL, class names) in an error response.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }
}

// Simpler alternative for tiny apps: annotate the exception class itself with
//     @ResponseStatus(HttpStatus.NOT_FOUND)
// and skip the handler. The @RestControllerAdvice approach scales better.

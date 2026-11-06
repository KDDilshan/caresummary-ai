package com.kavindu.caresummaryapi.common;

import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import com.kavindu.caresummaryapi.patient.PatientNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns Java exceptions into clean JSON error responses (RFC 9457 "problem details"),
 * so the client gets a clear message and a correct status code instead of a stack trace.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PatientNotFoundException.class)
    public ProblemDetail notFound(PatientNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequest(BadRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(FhirClientConnectionException.class)
    public ProblemDetail fhirDown(FhirClientConnectionException e) {
        log.error("Cannot reach FHIR server", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The health data server is not reachable. Is Docker running?");
    }
}

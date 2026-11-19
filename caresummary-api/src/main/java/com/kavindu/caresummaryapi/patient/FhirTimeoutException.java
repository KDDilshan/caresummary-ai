package com.kavindu.caresummaryapi.patient;

import java.time.Duration;

/** The FHIR server did not answer in time. Becomes HTTP 504 (see ApiExceptionHandler). */
public class FhirTimeoutException extends RuntimeException {

    public FhirTimeoutException(Duration timeout) {
        super("The health data server did not answer within " + timeout.toSeconds() + " seconds");
    }
}

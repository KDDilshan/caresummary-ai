package com.kavindu.caresummaryapi.common;

/** Thrown when the client sends bad input. Becomes HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

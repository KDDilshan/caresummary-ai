package com.kavindu.caresummaryapi.ai;

/** The AI is not configured or did not answer. Becomes HTTP 503. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }
}

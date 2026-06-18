package com.incidentiq.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an uploaded attachment cannot be accepted — either because the
 * antivirus scanner flagged a threat or because the scanner is unavailable.
 * Carries the HTTP status to return so the client gets a clear, actionable message
 * instead of a generic 500.
 */
public class AttachmentRejectedException extends RuntimeException {

    private final HttpStatus status;

    public AttachmentRejectedException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

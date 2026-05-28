package com.incidentiq.exception;

/**
 * Thrown when an incident with the given ID does not exist.
 */
public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(String message) {
        super(message);
    }
}

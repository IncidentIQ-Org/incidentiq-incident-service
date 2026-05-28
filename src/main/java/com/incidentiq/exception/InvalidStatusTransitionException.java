package com.incidentiq.exception;

/**
 * Thrown when an incident status transition violates lifecycle rules.
 * For example, attempting OPEN → RESOLVED (must go through IN_PROGRESS first).
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}

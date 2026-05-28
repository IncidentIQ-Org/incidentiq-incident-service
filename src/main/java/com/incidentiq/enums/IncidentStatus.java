package com.incidentiq.enums;

import java.util.List;

/**
 * Incident lifecycle statuses with enforced transition rules.
 * <p>
 * Valid transitions:
 * OPEN → IN_PROGRESS
 * IN_PROGRESS → RESOLVED
 * RESOLVED → CLOSED
 * CLOSED → (none — terminal state)
 */
public enum IncidentStatus {

    OPEN(List.of("IN_PROGRESS", "NEED_MORE_INFO")),
    IN_PROGRESS(List.of("RESOLVED", "ESCALATED", "NEED_MORE_INFO")),
    NEED_MORE_INFO(List.of("IN_PROGRESS")),
    ESCALATED(List.of("IN_PROGRESS", "RESOLVED")),
    RESOLVED(List.of("CLOSED", "OPEN")),
    CLOSED(List.of());

    private final List<String> allowedTransitions;

    IncidentStatus(List<String> allowedTransitions) {
        this.allowedTransitions = allowedTransitions;
    }

    /**
     * Checks whether transitioning from this status to the target status is valid.
     *
     * @param target the desired next status
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(IncidentStatus target) {
        return allowedTransitions.contains(target.name());
    }

    /**
     * Returns the list of statuses this status can transition to.
     */
    public List<String> getAllowedTransitions() {
        return allowedTransitions;
    }
}

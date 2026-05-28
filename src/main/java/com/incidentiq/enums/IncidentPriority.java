package com.incidentiq.enums;

/**
 * Priority levels for incidents, reflecting impact and urgency.
 */
public enum IncidentPriority {

    CRITICAL("Critical — Extreme impact, immediate action required"),
    HIGH("High — Critical impact, requires immediate attention"),
    MEDIUM("Medium — Significant impact, needs timely resolution"),
    LOW("Low — Minor impact, can be scheduled");

    private final String description;

    IncidentPriority(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

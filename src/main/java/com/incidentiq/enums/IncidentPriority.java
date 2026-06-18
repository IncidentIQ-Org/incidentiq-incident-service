package com.incidentiq.enums;

/**
 * Priority levels for incidents, reflecting impact and urgency.
 */
public enum IncidentPriority {

    CRITICAL("Critical — Extreme impact, immediate action required", 10),
    HIGH("High — Critical impact, requires immediate attention", 7),
    MEDIUM("Medium — Significant impact, needs timely resolution", 4),
    LOW("Low — Minor impact, can be scheduled", 1);

    private final String description;
    private final int workloadWeight;

    IncidentPriority(String description, int workloadWeight) {
        this.description = description;
        this.workloadWeight = workloadWeight;
    }

    public String getDescription() {
        return description;
    }

    public int getWorkloadWeight() {
        return workloadWeight;
    }
}

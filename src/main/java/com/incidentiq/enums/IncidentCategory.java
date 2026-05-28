package com.incidentiq.enums;

/**
 * Categories of incidents, mapping to responsible teams.
 */
public enum IncidentCategory {

    BACKEND("Backend Services"),
    FRONTEND("Frontend / UI"),
    INFRA("Infrastructure"),
    DATABASE("Database"),
    NETWORK("Network"),
    SECURITY("Security");

    private final String displayName;

    IncidentCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

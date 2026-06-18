package com.incidentiq.enums;

/**
 * Categories of incidents, mapping to responsible teams.
 */
public enum IncidentCategory {

    DATABASE("Database"),
    BACKEND("Backend"),
    FRONTEND("Frontend"),
    NETWORK("Network"),
    SECURITY("Security"),
    DEVOPS("DevOps"),
    CLOUD("Cloud"),
    APPLICATION_SUPPORT("Application Support");

    private final String displayName;

    IncidentCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

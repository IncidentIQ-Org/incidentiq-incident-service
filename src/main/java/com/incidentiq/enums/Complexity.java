package com.incidentiq.enums;

/**
 * Technical complexity of an incident — how difficult it is to resolve.
 * Completely independent of {@link IncidentPriority} (business impact).
 *
 * Examples:
 *  - EASY + CRITICAL: production login outage, simple fix but high impact
 *  - HARD + LOW: internal reporting bug, low impact but technically difficult
 *
 * Each value carries a {@code pointWeight} used by gamification and a
 * {@code minExperienceYears} hint used by the assignment engine.
 */
public enum Complexity {

    EASY("Easy — straightforward fix, well-understood", 10, 0),
    MEDIUM("Medium — structured troubleshooting required", 20, 2),
    HARD("Hard — deep investigation, specialist knowledge", 40, 4),
    COMPLEX("Critical/Complex — multi-step resolution, expert-level", 75, 6);

    private final String description;
    private final int pointWeight;
    private final int minExperienceYears;

    Complexity(String description, int pointWeight, int minExperienceYears) {
        this.description = description;
        this.pointWeight = pointWeight;
        this.minExperienceYears = minExperienceYears;
    }

    public String getDescription() {
        return description;
    }

    public int getPointWeight() {
        return pointWeight;
    }

    public int getMinExperienceYears() {
        return minExperienceYears;
    }
}

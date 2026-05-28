package com.incidentiq.constants;

/**
 * Application-wide constants. Avoids hardcoded strings/numbers in business logic.
 */
public final class IncidentConstants {

    private IncidentConstants() {
        // Utility class — prevent instantiation
    }

    // --- Error messages ---
    public static final String INCIDENT_NOT_FOUND = "Incident not found with id: %d";
    public static final String INVALID_STATUS_TRANSITION = "Invalid status transition from %s to %s. Allowed transitions: %s";

    // --- Log messages ---
    public static final String LOG_INCIDENT_CREATED = "Incident created with id: {}";
    public static final String LOG_INCIDENT_FETCHED = "Fetched incident with id: {}";
    public static final String LOG_INCIDENT_LIST_FETCHED = "Fetched incidents page: {}, size: {}, totalElements: {}";
    public static final String LOG_INCIDENT_UPDATED = "Updated incident with id: {}";
    public static final String LOG_INCIDENT_DELETED = "Deleted incident with id: {}";

    // --- Validation messages ---
    public static final String TITLE_REQUIRED = "Title is required";
    public static final String TITLE_MAX_LENGTH = "Title must not exceed 200 characters";
    public static final String DESCRIPTION_REQUIRED = "Description is required";
    public static final String DESCRIPTION_MAX_LENGTH = "Description must not exceed 2000 characters";
    public static final String CATEGORY_REQUIRED = "Category is required";
    public static final String PRIORITY_REQUIRED = "Priority is required";
    public static final String CREATED_BY_REQUIRED = "CreatedBy (reporter ID) is required";

    // --- Pagination defaults ---
    public static final String DEFAULT_PAGE = "0";
    public static final String DEFAULT_SIZE = "10";
    public static final String DEFAULT_SORT = "createdAt";
    public static final String DEFAULT_DIRECTION = "desc";
}

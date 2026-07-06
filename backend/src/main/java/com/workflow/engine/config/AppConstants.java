package com.workflow.engine.config;

/**
 * Application-wide constants.
 *
 * Contains only fixed application values: roles, status strings, and
 * API-level constants. Business rules (SLA calculations, priority weight
 * logic) remain in TicketService where they belong.
 */
public final class AppConstants {

    private AppConstants() {
        // Utility class - do not instantiate
    }

    // -------------------------------------------------------------------------
    // User Roles
    // -------------------------------------------------------------------------
    public static final String ROLE_ADMIN  = "admin";
    public static final String ROLE_STAFF  = "staff";
    public static final String ROLE_CONSUMER = "consumer";

    // -------------------------------------------------------------------------
    // Ticket Status Values
    // -------------------------------------------------------------------------
    public static final String STATUS_OPEN        = "Open";
    public static final String STATUS_ASSIGNED    = "Assigned";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_RESOLVED    = "Resolved";
    public static final String STATUS_CLOSED      = "Closed";

    // -------------------------------------------------------------------------
    // Ticket Priority Values
    // -------------------------------------------------------------------------
    public static final String PRIORITY_LOW      = "Low";
    public static final String PRIORITY_MEDIUM   = "Medium";
    public static final String PRIORITY_HIGH     = "High";
    public static final String PRIORITY_CRITICAL = "Critical";

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------
    /**
     * Placeholder token returned by the auth endpoints.
     * Replace with a proper JWT issuer before production use.
     */
    public static final String MOCK_TOKEN = "mock_jwt_token";

    // -------------------------------------------------------------------------
    // Default Values
    // -------------------------------------------------------------------------
    public static final String DEFAULT_ROLE            = ROLE_CONSUMER;
    public static final String DEFAULT_CREATED_BY_NAME = "system";
}

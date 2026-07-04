package com.workflow.engine.exception;

/**
 * Thrown when a requested resource (Ticket, User, Holiday, etc.) does not
 * exist in the database. Handled globally by {@link GlobalExceptionHandler}
 * which maps it to a 404 response, replacing the raw NoSuchElementException
 * that was previously leaking through the controller layer.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final Object resourceId;

    public ResourceNotFoundException(String resourceName, Object resourceId) {
        super(resourceName + " not found with id: " + resourceId);
        this.resourceName = resourceName;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceName = null;
        this.resourceId = null;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Object getResourceId() {
        return resourceId;
    }
}

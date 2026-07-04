package com.workflow.engine.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PUT /api/tickets/{id}/assign.
 */
public record AssignRequest(
        @NotBlank(message = "Assigned username is required") String assignedTo,
        @NotBlank(message = "Changed by name is required") String changedByName
) {
}

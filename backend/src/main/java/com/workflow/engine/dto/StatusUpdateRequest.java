package com.workflow.engine.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PUT /api/tickets/{id}/status.
 */
public record StatusUpdateRequest(
        @NotBlank(message = "Status is required") String status,
        @NotBlank(message = "Changed by name is required") String changedByName
) {
}

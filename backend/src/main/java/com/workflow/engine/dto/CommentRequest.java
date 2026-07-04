package com.workflow.engine.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/tickets/{id}/comments.
 */
public record CommentRequest(
        @NotBlank(message = "Author name is required") String authorName,
        @NotBlank(message = "Comment text is required") String text
) {
}

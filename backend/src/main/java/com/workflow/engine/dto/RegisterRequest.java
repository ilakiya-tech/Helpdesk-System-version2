package com.workflow.engine.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/users/register.
 */
public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        String role,

        @NotBlank(message = "Name is required")
        String name,

        @Email(message = "Invalid email format")
        String email,

        String mobile,
        String department,
        String availability,
        String secretKey
) {
}

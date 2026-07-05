package com.workflow.engine.controller;

import com.workflow.engine.entity.User;
import com.workflow.engine.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Endpoints for managing user accounts, permissions, roles, and profiles.")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.workflow.engine.service.EmailService emailService;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, com.workflow.engine.service.EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(
        summary = "Get all users (Paginated)",
        description = "Retrieves a paginated list of all active registered user profiles in the system.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved users page"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header")
    })
    public ResponseEntity<Page<User>> getAllUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = (page != null && size != null) ? PageRequest.of(page, size) : PageRequest.of(0, 10000);
        return ResponseEntity.ok(userRepository.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(
        summary = "Get user profile details",
        description = "Retrieves a single user's detailed information by their account ID.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "404", description = "User profile not found")
    })
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(
        summary = "Update user details",
        description = "Updates user account fields. Encrypts password changes using BCrypt before persistence.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User details updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "404", description = "User account not found")
    })
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody @Valid User updated) {
        return userRepository.findById(id)
                .map(existing -> {
                    existing.setName(updated.getName());
                    existing.setEmail(updated.getEmail());
                    existing.setMobile(updated.getMobile());
                    existing.setDepartment(updated.getDepartment());
                    existing.setAvailability(updated.getAvailability());
                    existing.setDesignation(updated.getDesignation());
                    existing.setSpecialization(updated.getSpecialization());
                    if (updated.getEnabled() != null) {
                        existing.setEnabled(updated.getEnabled());
                    }
                    if (updated.getRole() != null) {
                        existing.setRole(updated.getRole());
                    }
                    if (updated.getPassword() != null && !updated.getPassword().isEmpty() && !updated.getPassword().equals(existing.getPassword())) {
                        existing.setPassword(passwordEncoder.encode(updated.getPassword()));
                        if (existing.getEmail() != null && !existing.getEmail().isBlank()) {
                            emailService.sendPasswordResetEmail(existing.getEmail(), existing.getName(), updated.getPassword());
                        }
                    }
                    return ResponseEntity.ok(userRepository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(
        summary = "Update user availability status",
        description = "Updates only the availability status of a staff member. Fast atomic endpoint.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<?> updateAvailability(@PathVariable Long id, @RequestBody java.util.Map<String, String> payload) {
        return ResponseEntity.badRequest().body(java.util.Map.of(
            "success", false,
            "message", "Direct availability updates are disabled. Availability is managed exclusively via the Leave Request workflow."
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Delete user account",
        description = "Permanently deletes a user account from the system database.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

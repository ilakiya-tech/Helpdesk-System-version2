package com.workflow.engine.controller;

import com.workflow.engine.entity.Holiday;
import com.workflow.engine.repository.HolidayRepository;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/holidays")
@Tag(name = "Holiday Management", description = "Endpoints for managing organization and public holidays.")
public class HolidayController {

    private final HolidayRepository holidayRepository;

    public HolidayController(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(
        summary = "Get all holidays (Paginated)",
        description = "Retrieves a paginated list of all public and company holidays configured in the system.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved holidays"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header")
    })
    public ResponseEntity<Page<Holiday>> getAllHolidays(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = (page != null && size != null) ? PageRequest.of(page, size) : PageRequest.of(0, 10000);
        return ResponseEntity.ok(holidayRepository.findAll(pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Create a new holiday",
        description = "Registers a new holiday in the organization calendar.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Holiday created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid holiday input data"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<?> createHoliday(@RequestBody @Valid Holiday holiday) {
        if (holidayRepository.existsByDate(holiday.getDate())) {
            java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("success", false);
            err.put("message", "A holiday on this date already exists");
            return ResponseEntity.status(409).body(err);
        }
        return ResponseEntity.ok(holidayRepository.save(holiday));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Update a holiday",
        description = "Modifies the details of an existing holiday by ID.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Holiday updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid holiday input data"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Holiday not found")
    })
    public ResponseEntity<Holiday> updateHoliday(@PathVariable Long id, @RequestBody @Valid Holiday updated) {
        return holidayRepository.findById(id)
                .map(existing -> {
                    existing.setName(updated.getName());
                    existing.setDate(updated.getDate());
                    existing.setType(updated.getType());
                    return ResponseEntity.ok(holidayRepository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Delete a holiday",
        description = "Permanently deletes a registered holiday by ID.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Holiday deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Holiday not found")
    })
    public ResponseEntity<Void> deleteHoliday(@PathVariable Long id) {
        if (!holidayRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        holidayRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}


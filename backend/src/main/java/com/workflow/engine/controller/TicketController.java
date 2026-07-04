package com.workflow.engine.controller;

import com.workflow.engine.config.AppConstants;
import com.workflow.engine.dto.AssignRequest;
import com.workflow.engine.dto.CommentRequest;
import com.workflow.engine.dto.StatusUpdateRequest;
import com.workflow.engine.entity.Comment;
import com.workflow.engine.entity.Ticket;
import com.workflow.engine.exception.ResourceNotFoundException;
import com.workflow.engine.service.TicketService;
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

import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Ticket Management", description = "Endpoints for creating, assigning, updating status, and adding comments to support tickets.")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(
        summary = "Create a new ticket",
        description = "Creates a new helpdesk support ticket. Calculates response and resolution deadlines from SLA rules.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ticket created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid ticket input data"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Ticket> createTicket(@RequestBody @Valid Ticket ticket) {
        String createdByName = ticket.getCustomerName() != null
                ? ticket.getCustomerName()
                : AppConstants.DEFAULT_CREATED_BY_NAME;
        Ticket saved = ticketService.createTicket(ticket, createdByName);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(
        summary = "Get all sorted tickets (Paginated)",
        description = "Retrieves all active, non-resolved tickets sorted in real-time by the in-memory Max-Heap priority. Supports optional page and size parameters.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved tickets"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header")
    })
    public ResponseEntity<Page<Ticket>> getAllTicketsSortedByPriority(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = (page != null && size != null) ? PageRequest.of(page, size) : PageRequest.of(0, 10000);
        return ResponseEntity.ok(ticketService.getTicketsSortedByPriority(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(
        summary = "Get ticket details",
        description = "Retrieves a ticket's complete profile, comments list, and state transition history.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ticket found"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    public ResponseEntity<Map<String, Object>> getTicketDetail(@PathVariable Long id) {
        Map<String, Object> detail = ticketService.getTicketDetail(id);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(
        summary = "Update ticket status",
        description = "Updates the status of a ticket. Triggers first-response timestamps on 'In Progress' and freezes SLA tracking on 'Resolved'.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    public ResponseEntity<Ticket> updateStatus(
            @PathVariable Long id,
            @RequestBody @Valid StatusUpdateRequest request) {
        try {
            Ticket updated = ticketService.updateStatus(id, request.status(), request.changedByName());
            return ResponseEntity.ok(updated);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Assign ticket to staff",
        description = "Assigns the support ticket to a specific staff member. Automatically logs assignment timestamps.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ticket assigned successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    public ResponseEntity<Ticket> assignTicket(
            @PathVariable Long id,
            @RequestBody @Valid AssignRequest request) {
        try {
            Ticket updated = ticketService.assignTicket(id, request.assignedTo(), request.changedByName());
            return ResponseEntity.ok(updated);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(
        summary = "Add a comment",
        description = "Appends a new note or comment to the ticket's message timeline.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Comment added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Comment> addComment(
            @PathVariable Long id,
            @RequestBody @Valid CommentRequest request) {
        Comment saved = ticketService.addComment(id, request.authorName(), request.text());
        return ResponseEntity.ok(saved);
    }
}

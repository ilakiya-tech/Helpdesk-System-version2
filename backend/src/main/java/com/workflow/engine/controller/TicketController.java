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
import java.util.List;
import com.workflow.engine.entity.User;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Ticket Management", description = "Endpoints for creating, assigning, updating status, and adding comments to support tickets.")
public class TicketController {

    private final TicketService ticketService;
    private final com.workflow.engine.service.EmailService emailService;
    private final com.workflow.engine.service.NotificationService notificationService;
    private final com.workflow.engine.repository.UserRepository userRepository;
    private final com.workflow.engine.repository.TicketRepository ticketRepository;
    private final com.workflow.engine.repository.CommentRepository commentRepository;

    public TicketController(TicketService ticketService,
                            com.workflow.engine.service.EmailService emailService,
                            com.workflow.engine.service.NotificationService notificationService,
                            com.workflow.engine.repository.UserRepository userRepository,
                            com.workflow.engine.repository.TicketRepository ticketRepository,
                            com.workflow.engine.repository.CommentRepository commentRepository) {
        this.ticketService = ticketService;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
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

        if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
            emailService.sendTicketCreatedEmail(
                saved.getEmail(),
                saved.getCustomerName(),
                saved.getId(),
                saved.getTitle(),
                saved.getPriority()
            );
        }

        userRepository.findByUsername(createdByName).ifPresent(u -> {
            notificationService.createNotification(
                u.getId(),
                "Ticket Created",
                "Your support ticket #" + saved.getId() + " (" + saved.getTitle() + ") was successfully created.",
                "TICKET_CREATED",
                saved.getId()
            );
        });

        if ("High".equalsIgnoreCase(saved.getPriority()) || "Critical".equalsIgnoreCase(saved.getPriority())) {
            notificationService.notifyAdmins(
                "High Priority Ticket Waiting",
                "Ticket #" + saved.getId() + " (" + saved.getTitle() + ") is waiting with priority: " + saved.getPriority(),
                "HIGH_PRIORITY_WAITING",
                saved.getId()
            );
        }

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
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "false") boolean active) {
        Pageable pageable = (page != null && size != null) ? PageRequest.of(page, size) : PageRequest.of(0, 10000);
        if (!active) {
            // Retrieve all tickets from database directly, sorted by id descending
            List<Ticket> dbTickets = ticketRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), dbTickets.size());
            if (start > dbTickets.size()) {
                return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(new java.util.ArrayList<>(), pageable, dbTickets.size()));
            }
            return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(dbTickets.subList(start, end), pageable, dbTickets.size()));
        }
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
            Map<String, Object> oldDetail = ticketService.getTicketDetail(id);
            String tempStatus = "Open";
            if (oldDetail != null && oldDetail.get("ticket") != null) {
                Ticket t = (Ticket) oldDetail.get("ticket");
                tempStatus = t.getStatus();
            }
            final String oldStatus = tempStatus;

            Ticket updated = ticketService.updateStatus(id, request.status(), request.changedByName());

            if (updated.getEmail() != null && !updated.getEmail().isBlank()) {
                if ("Resolved".equalsIgnoreCase(updated.getStatus())) {
                    emailService.sendTicketResolvedEmail(updated.getEmail(), updated.getCustomerName(), updated.getId(), updated.getTitle());
                } else {
                    emailService.sendTicketStatusChangedEmail(updated.getEmail(), updated.getCustomerName(), updated.getId(), updated.getTitle(), oldStatus, updated.getStatus());
                }
            }

            userRepository.findByUsername(updated.getCustomerName()).ifPresent(u -> {
                String title = "Resolved".equalsIgnoreCase(updated.getStatus()) ? "Ticket Resolved" : "Ticket Status Updated";
                String msg = "Resolved".equalsIgnoreCase(updated.getStatus())
                    ? "Your ticket #" + updated.getId() + " has been resolved."
                    : "The status of ticket #" + updated.getId() + " has been changed to " + updated.getStatus() + ".";
                notificationService.createNotification(u.getId(), title, msg, "Resolved".equalsIgnoreCase(updated.getStatus()) ? "TICKET_RESOLVED" : "TICKET_STATUS_CHANGED", updated.getId());
            });

            if (updated.getAssignedTo() != null) {
                userRepository.findByUsername(updated.getAssignedTo()).ifPresent(staff -> {
                    if (staff.getEmail() != null && !staff.getEmail().isBlank()) {
                        emailService.sendTicketStatusChangedEmail(staff.getEmail(), staff.getName(), updated.getId(), updated.getTitle(), oldStatus, updated.getStatus());
                    }
                    notificationService.createNotification(
                        staff.getId(),
                        "Ticket Status Changed",
                        "Ticket #" + updated.getId() + " assigned to you has changed status to " + updated.getStatus() + ".",
                        "TICKET_STATUS_CHANGED",
                        updated.getId()
                    );
                });
            }

            if ("BREACHED".equalsIgnoreCase(updated.getSlaStatus())) {
                notificationService.notifyAdmins(
                    "Ticket Overdue",
                    "Ticket #" + updated.getId() + " has breached SLA.",
                    "TICKET_OVERDUE",
                    updated.getId()
                );
            }

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

            java.util.Optional<com.workflow.engine.entity.User> staffOpt = userRepository.findByUsername(request.assignedTo());
            if (staffOpt.isPresent()) {
                com.workflow.engine.entity.User staff = staffOpt.get();
                notificationService.createNotification(
                    staff.getId(),
                    "Ticket Assigned",
                    "Ticket #" + updated.getId() + " (" + updated.getTitle() + ") has been assigned to you.",
                    "TICKET_ASSIGNED",
                    updated.getId()
                );

                if (staff.getEmail() != null && !staff.getEmail().isBlank()) {
                    emailService.sendTicketAssignedEmail(
                        staff.getEmail(),
                        staff.getName(),
                        updated.getId(),
                        updated.getTitle(),
                        updated.getPriority()
                    );
                }
            }

            return ResponseEntity.ok(updated);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
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

        // Resolve author role and set it on the comment record
        String authorRole = userRepository.findByUsername(request.authorName())
                .map(User::getRole)
                .orElse("consumer");
        saved.setAuthorRole(authorRole);
        saved = commentRepository.save(saved);

        // Generate in-app notifications
        Ticket ticket = ticketRepository.findById(id).orElse(null);
        if (ticket != null) {
            String msg = request.authorName() + " (" + authorRole.toUpperCase() + ") commented on ticket #" + ticket.getId() + ": \"" + request.text() + "\"";
            
            // Notify customer (if they didn't write the comment)
            if (!request.authorName().equalsIgnoreCase(ticket.getCustomerName())) {
                userRepository.findByUsername(ticket.getCustomerName()).ifPresent(customer -> {
                    notificationService.createNotification(customer.getId(), "New Comment Added", msg, "COMMENT_ADDED", ticket.getId());
                });
            }
            
            // Notify assigned staff member (if they didn't write the comment)
            if (ticket.getAssignedTo() != null && !request.authorName().equalsIgnoreCase(ticket.getAssignedTo())) {
                userRepository.findByUsername(ticket.getAssignedTo()).ifPresent(staff -> {
                    notificationService.createNotification(staff.getId(), "New Comment Added", msg, "COMMENT_ADDED", ticket.getId());
                });
            }
            
            // Notify administrators (if the author is not an administrator)
            if (!"admin".equalsIgnoreCase(authorRole)) {
                notificationService.notifyAdmins("New Comment Added", msg, "COMMENT_ADDED", ticket.getId());
            }
        }

        return ResponseEntity.ok(saved);
    }
}

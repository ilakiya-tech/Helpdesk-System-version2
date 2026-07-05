package com.workflow.engine.controller;

import com.workflow.engine.entity.LeaveRequest;
import com.workflow.engine.entity.User;
import com.workflow.engine.repository.LeaveRequestRepository;
import com.workflow.engine.repository.UserRepository;
import com.workflow.engine.service.EmailService;
import com.workflow.engine.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/leaves")
@Tag(name = "Leave Management", description = "Endpoints for managing staff leave requests and approvals.")
public class LeaveRequestController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public LeaveRequestController(LeaveRequestRepository leaveRequestRepository,
                                  UserRepository userRepository,
                                  NotificationService notificationService,
                                  EmailService emailService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    private Optional<User> getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username);
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Submit a leave request", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> submitLeaveRequest(@RequestBody Map<String, String> payload) {
        try {
            Optional<User> curOpt = getCurrentUser();
            if (curOpt.isEmpty()) {
                return ResponseEntity.status(401).build();
            }
            User staff = curOpt.get();

            String leaveType = payload.get("leaveType");
            String reason = payload.get("reason");
            LocalDate startDate = LocalDate.parse(payload.get("startDate"));
            LocalDate endDate = LocalDate.parse(payload.get("endDate"));

            if (leaveType == null || reason == null || startDate == null || endDate == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing required fields"));
            }

            LeaveRequest request = new LeaveRequest(staff.getId(), leaveType, reason, startDate, endDate);
            LeaveRequest saved = leaveRequestRepository.save(request);

            // Notify Admins in-app
            notificationService.notifyAdmins(
                    "New leave request submitted",
                    "Staff " + staff.getName() + " submitted a leave request (" + leaveType + ") from " + startDate + " to " + endDate + ".",
                    "LEAVE_SUBMITTED",
                    saved.getId()
            );

            return ResponseEntity.ok(Map.of("success", true, "leaveRequest", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid request: " + e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Get leave requests", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> getLeaveRequests() {
        Optional<User> curOpt = getCurrentUser();
        if (curOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        User user = curOpt.get();

        if ("admin".equalsIgnoreCase(user.getRole())) {
            List<LeaveRequest> list = leaveRequestRepository.findAll();
            return ResponseEntity.ok(list);
        } else {
            List<LeaveRequest> list = leaveRequestRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
            return ResponseEntity.ok(list);
        }
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a leave request", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> approveLeaveRequest(@PathVariable Long id) {
        Optional<User> adminOpt = getCurrentUser();
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        User admin = adminOpt.get();

        return leaveRequestRepository.findById(id).map(request -> {
            if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Leave request has already been reviewed"));
            }

            request.setStatus("APPROVED");
            request.setReviewedBy(admin.getId());
            request.setReviewedAt(LocalDateTime.now());
            leaveRequestRepository.save(request);

            // 1. Update staff availability to "on_leave"
            userRepository.findById(request.getUserId()).ifPresent(staff -> {
                staff.setAvailability("on_leave");
                userRepository.save(staff);

                // 2. Notify staff in-app
                notificationService.createNotification(
                        staff.getId(),
                        "Leave request approved",
                        "Your leave request (" + request.getLeaveType() + ") from " + request.getStartDate() + " to " + request.getEndDate() + " has been approved.",
                        "LEAVE_APPROVED",
                        request.getId()
                );

                // 3. Send email notification to staff
                if (staff.getEmail() != null) {
                    emailService.sendLeaveRequestStatusEmail(
                            staff.getEmail(),
                            staff.getName(),
                            "APPROVED",
                            request.getLeaveType(),
                            request.getStartDate().toString(),
                            request.getEndDate().toString()
                    );
                }
            });

            return ResponseEntity.ok(Map.of("success", true));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject a leave request", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> rejectLeaveRequest(@PathVariable Long id) {
        Optional<User> adminOpt = getCurrentUser();
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        User admin = adminOpt.get();

        return leaveRequestRepository.findById(id).map(request -> {
            if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Leave request has already been reviewed"));
            }

            request.setStatus("REJECTED");
            request.setReviewedBy(admin.getId());
            request.setReviewedAt(LocalDateTime.now());
            leaveRequestRepository.save(request);

            // 1. Notify staff in-app
            userRepository.findById(request.getUserId()).ifPresent(staff -> {
                notificationService.createNotification(
                        staff.getId(),
                        "Leave request rejected",
                        "Your leave request (" + request.getLeaveType() + ") from " + request.getStartDate() + " to " + request.getEndDate() + " has been rejected.",
                        "LEAVE_REJECTED",
                        request.getId()
                );

                // 2. Send email notification to staff
                if (staff.getEmail() != null) {
                    emailService.sendLeaveRequestStatusEmail(
                            staff.getEmail(),
                            staff.getName(),
                            "REJECTED",
                            request.getLeaveType(),
                            request.getStartDate().toString(),
                            request.getEndDate().toString()
                    );
                }
            });

            return ResponseEntity.ok(Map.of("success", true));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}

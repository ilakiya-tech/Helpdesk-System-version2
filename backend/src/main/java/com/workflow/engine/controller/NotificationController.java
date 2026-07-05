package com.workflow.engine.controller;

import com.workflow.engine.entity.Notification;
import com.workflow.engine.entity.User;
import com.workflow.engine.repository.UserRepository;
import com.workflow.engine.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notification Management", description = "Endpoints for retrieving and managing in-app notifications.")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    private Optional<User> getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(summary = "Get user notifications", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> getNotifications() {
        return getCurrentUser()
                .map(u -> ResponseEntity.ok(notificationService.getNotificationsForUser(u.getId())))
                .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(summary = "Get user unread notifications count", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> getUnreadCount() {
        return getCurrentUser()
                .map(u -> ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(u.getId()))))
                .orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(summary = "Mark notification as read", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        return getCurrentUser()
                .map(u -> {
                    boolean success = notificationService.markAsRead(id, u.getId());
                    return ResponseEntity.ok(Map.of("success", success));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/read-all")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(summary = "Mark all notifications as read", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> markAllAsRead() {
        return getCurrentUser()
                .map(u -> {
                    notificationService.markAllAsRead(u.getId());
                    return ResponseEntity.ok(Map.of("success", true));
                })
                .orElse(ResponseEntity.status(401).build());
    }
}

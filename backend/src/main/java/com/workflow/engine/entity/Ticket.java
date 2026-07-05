package com.workflow.engine.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.workflow.engine.config.AppConstants;

/**
 * Ticket entity - the core incident/asset-scheduling record.
 *
 * priorityWeight is a derived integer used exclusively by the in-memory
 * MaxHeapEngine to order tickets. It is recomputed by TicketService
 * whenever priority/dueDate change or on the periodic escalation sweep,
 * and is persisted so the heap can be rehydrated identically on restart.
 */
@Entity
@Table(name = "tickets", indexes = {
    @Index(name = "idx_tickets_status", columnList = "status"),
    @Index(name = "idx_tickets_sla_status", columnList = "slaStatus"),
    @Index(name = "idx_tickets_priority", columnList = "priority"),
    @Index(name = "idx_tickets_assigned_to", columnList = "assignedTo"),
    @Index(name = "idx_tickets_response_sla_deadline", columnList = "responseSlaDeadline"),
    @Index(name = "idx_tickets_resolution_sla_deadline", columnList = "resolutionSlaDeadline"),
    @Index(name = "idx_tickets_created_at", columnList = "createdAt")
})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @jakarta.validation.constraints.NotBlank(message = "Title is required")
    private String title;

    @Column(length = 4000)
    @jakarta.validation.constraints.Size(max = 4000, message = "Description must not exceed 4000 characters")
    private String description;

    /** One of: "Low", "Medium", "High", "Critical" */
    @Column(nullable = false)
    @jakarta.validation.constraints.NotBlank(message = "Priority is required")
    private String priority;

    private String category;

    /** One of: "Open", "In Progress", "Resolved" */
    @Column(nullable = false)
    private String status;

    private String customerName;

    @jakarta.validation.constraints.Email(message = "Invalid email format")
    private String email;

    private String mobile;

    /** Username or name of the staff member assigned to this ticket */
    private String assignedTo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime dueDate;

    /** Derived field driving Max-Heap ordering. Higher = more urgent. */
    @Column(nullable = false)
    private int priorityWeight;

    private LocalDateTime assignedAt;
    
    private LocalDateTime firstRespondedAt;
    
    private LocalDateTime resolvedAt;
    
    private LocalDateTime responseSlaDeadline;
    
    private LocalDateTime resolutionSlaDeadline;
    
    private String slaStatus;

    @Column(name = "created_by_name")
    private String createdByName;

    @Transient
    private java.util.List<ActivityHistory> activityHistory = new java.util.ArrayList<>();


    public Ticket() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public int getPriorityWeight() {
        return priorityWeight;
    }

    public void setPriorityWeight(int priorityWeight) {
        this.priorityWeight = priorityWeight;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public LocalDateTime getFirstRespondedAt() {
        return firstRespondedAt;
    }

    public void setFirstRespondedAt(LocalDateTime firstRespondedAt) {
        this.firstRespondedAt = firstRespondedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getResponseSlaDeadline() {
        return responseSlaDeadline;
    }

    public void setResponseSlaDeadline(LocalDateTime responseSlaDeadline) {
        this.responseSlaDeadline = responseSlaDeadline;
    }

    public LocalDateTime getResolutionSlaDeadline() {
        return resolutionSlaDeadline;
    }

    public void setResolutionSlaDeadline(LocalDateTime resolutionSlaDeadline) {
        this.resolutionSlaDeadline = resolutionSlaDeadline;
    }

    public String getSlaStatus() {
        return slaStatus;
    }

    public void setSlaStatus(String slaStatus) {
        this.slaStatus = slaStatus;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public java.util.List<ActivityHistory> getActivityHistory() {
        return activityHistory;
    }

    public void setActivityHistory(java.util.List<ActivityHistory> activityHistory) {
        this.activityHistory = activityHistory;
    }

    @Transient
    public Long getRemainingTime() {
        if (AppConstants.STATUS_RESOLVED.equals(status) || "Closed".equals(status)) {
            return null;
        }
        LocalDateTime activeDeadline = (firstRespondedAt == null) ? responseSlaDeadline : resolutionSlaDeadline;
        if (activeDeadline == null) {
            return null;
        }
        return java.time.Duration.between(LocalDateTime.now(), activeDeadline).toMinutes();
    }
}

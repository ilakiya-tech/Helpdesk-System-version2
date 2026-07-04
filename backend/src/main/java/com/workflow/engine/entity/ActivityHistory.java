package com.workflow.engine.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_history", indexes = {
    @Index(name = "idx_activity_history_ticket_id", columnList = "ticketId"),
    @Index(name = "idx_activity_history_created_at", columnList = "createdAt")
})
public class ActivityHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ticketId;

    /** e.g. "CREATED", "STATUS_CHANGED:Open->In Progress", "ASSIGNED:staffUser" */
    private String action;

    private String changedByName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ActivityHistory() {
    }

    public ActivityHistory(Long ticketId, String action, String changedByName, LocalDateTime createdAt) {
        this.ticketId = ticketId;
        this.action = action;
        this.changedByName = changedByName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getChangedByName() {
        return changedByName;
    }

    public void setChangedByName(String changedByName) {
        this.changedByName = changedByName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

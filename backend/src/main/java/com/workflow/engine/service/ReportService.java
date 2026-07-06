package com.workflow.engine.service;

import com.workflow.engine.config.AppConstants;
import com.workflow.engine.entity.Ticket;
import com.workflow.engine.entity.User;
import com.workflow.engine.repository.TicketRepository;
import com.workflow.engine.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes aggregate ticket-state and workload metrics for operational dashboards and reports.
 * Completely free of SLA calculations.
 */
@Service
public class ReportService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public ReportService(TicketRepository ticketRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStatusSummary() {
        List<Ticket> all = ticketRepository.findAll();
        long total = all.size();

        // ── Status counts ──────────────────────────────────────────────────────
        long open       = all.stream().filter(t -> AppConstants.STATUS_OPEN.equals(t.getStatus())).count();
        long assigned   = all.stream().filter(t -> AppConstants.STATUS_ASSIGNED.equals(t.getStatus())).count();
        long inProgress = all.stream().filter(t -> AppConstants.STATUS_IN_PROGRESS.equals(t.getStatus())).count();
        long resolved   = all.stream().filter(t -> AppConstants.STATUS_RESOLVED.equals(t.getStatus())).count();
        long closed     = all.stream().filter(t -> AppConstants.STATUS_CLOSED.equals(t.getStatus())).count();

        // ── Overdue: non-terminal tickets whose due date has passed ──
        LocalDateTime now = LocalDateTime.now();
        long overdue = all.stream()
                .filter(t -> !AppConstants.STATUS_RESOLVED.equals(t.getStatus())
                          && !AppConstants.STATUS_CLOSED.equals(t.getStatus()))
                .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(now))
                .count();

        // ── Tickets by Priority ───────────────────────────────────────────────
        Map<String, Long> byPriority = new LinkedHashMap<>();
        byPriority.put("Critical", all.stream().filter(t -> "Critical".equalsIgnoreCase(t.getPriority())).count());
        byPriority.put("High",     all.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count());
        byPriority.put("Medium",   all.stream().filter(t -> "Medium".equalsIgnoreCase(t.getPriority())).count());
        byPriority.put("Low",      all.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count());

        // ── Tickets by Category (Department) ──────────────────────────────────
        Map<String, Long> byDepartment = all.stream()
                .filter(t -> t.getCategory() != null && !t.getCategory().isBlank())
                .collect(Collectors.groupingBy(Ticket::getCategory, Collectors.counting()));

        // ── Staff Workload (Active tickets per staff) ──────────────────────────
        List<User> allUsers = userRepository.findAll();
        List<User> staffMembers = allUsers.stream()
                .filter(u -> "staff".equalsIgnoreCase(u.getRole()))
                .toList();

        Map<String, Long> staffWorkload = new LinkedHashMap<>();
        for (User staff : staffMembers) {
            long activeCount = all.stream()
                    .filter(t -> !AppConstants.STATUS_RESOLVED.equals(t.getStatus())
                              && !AppConstants.STATUS_CLOSED.equals(t.getStatus()))
                    .filter(t -> staff.getUsername().equalsIgnoreCase(t.getAssignedTo())
                              || staff.getName().equalsIgnoreCase(t.getAssignedTo()))
                    .count();
            staffWorkload.put(staff.getName() != null ? staff.getName() : staff.getUsername(), activeCount);
        }

        // ── Monthly Ticket Trend (Last 6 Months) ──────────────────────────────
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");
        Map<String, Long> monthlyTrend = all.stream()
                .filter(t -> t.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().format(formatter),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        // ── User role counts ──────────────────────────────────────────────────
        long activeStaff     = allUsers.stream()
                .filter(u -> "staff".equalsIgnoreCase(u.getRole()))
                .filter(u -> !"on_leave".equalsIgnoreCase(u.getAvailability()))
                .count();
        long totalConsumers  = allUsers.stream()
                .filter(u -> AppConstants.ROLE_CONSUMER.equalsIgnoreCase(u.getRole()))
                .count();
        long totalUsers      = allUsers.size();

        // ── Build summary map ─────────────────────────────────────────────────
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("open",           open);
        summary.put("assigned",       assigned);
        summary.put("inProgress",     inProgress);
        summary.put("resolved",       resolved);
        summary.put("closed",         closed);
        summary.put("overdue",        overdue);
        summary.put("total",          total);
        summary.put("ticketsByPriority",   byPriority);
        summary.put("ticketsByDepartment", byDepartment);
        summary.put("staffWorkload",       staffWorkload);
        summary.put("monthlyTrend",        monthlyTrend);
        summary.put("activeStaff",    activeStaff);
        summary.put("totalConsumers", totalConsumers);
        summary.put("totalUsers",     totalUsers);

        return summary;
    }
}

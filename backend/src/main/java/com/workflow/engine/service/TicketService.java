package com.workflow.engine.service;

import com.workflow.engine.config.AppConstants;
import com.workflow.engine.config.SlaProperties;
import com.workflow.engine.dsa.MaxHeapEngine;
import com.workflow.engine.dsa.TrieEngine;
import com.workflow.engine.entity.ActivityHistory;
import com.workflow.engine.entity.Comment;
import com.workflow.engine.entity.Ticket;
import com.workflow.engine.exception.ResourceNotFoundException;
import com.workflow.engine.repository.ActivityHistoryRepository;
import com.workflow.engine.repository.CommentRepository;
import com.workflow.engine.repository.TicketRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TicketService is the single coordination point between the persistent
 * store (PostgreSQL, via TicketRepository) and the two in-memory engines
 * (MaxHeapEngine, TrieEngine). Every write path here follows the same
 * pattern: commit the DB transaction first (source of truth), then apply
 * the equivalent mutation to the in-memory structures so reads stay fast
 * and consistent without ever hitting the database.
 *
 * Phase 4: SLA deadlines are automatically calculated from priority rules
 * on ticket creation. The escalationSweep() scheduler updates slaStatus
 * for all active tickets every minute.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final ActivityHistoryRepository activityHistoryRepository;
    private final MaxHeapEngine maxHeapEngine;
    private final TrieEngine trieEngine;
    private final SlaProperties slaProperties;

    public TicketService(TicketRepository ticketRepository,
                          CommentRepository commentRepository,
                          ActivityHistoryRepository activityHistoryRepository,
                          MaxHeapEngine maxHeapEngine,
                          TrieEngine trieEngine,
                          SlaProperties slaProperties) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.activityHistoryRepository = activityHistoryRepository;
        this.maxHeapEngine = maxHeapEngine;
        this.trieEngine = trieEngine;
        this.slaProperties = slaProperties;
    }

    /**
     * Hydrates the Trie and Max-Heap from PostgreSQL on startup.
     */
    @PostConstruct
    public void hydrateEngines() {
        maxHeapEngine.clear();
        trieEngine.clear();
        List<Ticket> allTickets = ticketRepository.findAll();
        for (Ticket t : allTickets) {
            trieEngine.insert(t.getTitle(), t.getId());
            // Only active (non-terminal) tickets belong in the max-heap
            boolean terminal = AppConstants.STATUS_RESOLVED.equals(t.getStatus())
                    || AppConstants.STATUS_CLOSED.equals(t.getStatus());
            if (!terminal) {
                maxHeapEngine.insert(t.getId(), t.getPriorityWeight());
            }
        }
    }

    // -------------------------------------------------------------------------
    // SLA Helper Methods
    // -------------------------------------------------------------------------

    /**
     * Returns the SLA rule for the given priority (case-insensitive).
     * Falls back to Low if the priority is unknown.
     */
    private SlaProperties.Rule getSlaRule(String priority) {
        if (priority == null) return slaProperties.getRules().getOrDefault("low", defaultRule());
        return slaProperties.getRules().getOrDefault(priority.toLowerCase(), defaultRule());
    }

    private SlaProperties.Rule defaultRule() {
        SlaProperties.Rule r = new SlaProperties.Rule();
        r.setResponseMinutes(480);
        r.setResolutionMinutes(4320);
        return r;
    }

    /**
     * Determines and sets the SLA status for an active ticket.
     * Must NOT be called for resolved or closed tickets (their SLA is frozen).
     */
    private void recalculateSlaStatus(Ticket ticket) {
        boolean isTerminal = AppConstants.STATUS_RESOLVED.equals(ticket.getStatus())
                || "Closed".equals(ticket.getStatus());
        if (isTerminal) return; // SLA frozen once resolved/closed

        LocalDateTime now = LocalDateTime.now();
        SlaProperties.Rule rule = getSlaRule(ticket.getPriority());

        LocalDateTime activeDeadline;
        int totalMinutes;

        if (ticket.getFirstRespondedAt() == null) {
            // Still awaiting first response — measure against response deadline
            activeDeadline = ticket.getResponseSlaDeadline();
            totalMinutes = rule.getResponseMinutes();
        } else {
            // Responded — now measuring against resolution deadline
            activeDeadline = ticket.getResolutionSlaDeadline();
            totalMinutes = rule.getResolutionMinutes();
        }

        if (activeDeadline == null) {
            ticket.setSlaStatus(AppConstants.SLA_STATUS_IN_SLA);
            return;
        }

        long remainingMinutes = Duration.between(now, activeDeadline).toMinutes();

        if (remainingMinutes < 0) {
            ticket.setSlaStatus(AppConstants.SLA_STATUS_BREACHED);
        } else {
            double ratio = (double) remainingMinutes / totalMinutes;
            if (ratio <= 0.20) {
                ticket.setSlaStatus(AppConstants.SLA_STATUS_AT_RISK);
            } else {
                ticket.setSlaStatus(AppConstants.SLA_STATUS_IN_SLA);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Ticket CRUD Operations
    // -------------------------------------------------------------------------

    @Transactional
    public Ticket createTicket(Ticket ticket, String createdByName) {
        ticket.setCreatedAt(LocalDateTime.now());
        if (ticket.getStatus() == null) {
            ticket.setStatus(AppConstants.STATUS_OPEN);
        }
        ticket.setPriorityWeight(computePriorityWeight(ticket));

        // Calculate SLA deadlines from priority rules
        SlaProperties.Rule rule = getSlaRule(ticket.getPriority());
        ticket.setResponseSlaDeadline(ticket.getCreatedAt().plusMinutes(rule.getResponseMinutes()));
        ticket.setResolutionSlaDeadline(ticket.getCreatedAt().plusMinutes(rule.getResolutionMinutes()));
        ticket.setSlaStatus(AppConstants.SLA_STATUS_IN_SLA);

        Ticket saved = ticketRepository.save(ticket);

        trieEngine.insert(saved.getTitle(), saved.getId());
        if (!AppConstants.STATUS_RESOLVED.equals(saved.getStatus())) {
            maxHeapEngine.insert(saved.getId(), saved.getPriorityWeight());
        }

        logActivity(saved.getId(), "CREATED", createdByName);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Ticket> getTicketById(Long id) {
        return ticketRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTicketDetail(Long id) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(id);
        if (ticketOpt.isEmpty()) {
            return null;
        }
        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(id);
        List<ActivityHistory> history = activityHistoryRepository.findByTicketIdOrderByCreatedAtAsc(id);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("ticket", ticketOpt.get());
        detail.put("comments", comments);
        detail.put("history", history);
        return detail;
    }

    /**
     * Returns every non-resolved ticket ordered by live heap priority (descending).
     */
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsSortedByPriority() {
        List<MaxHeapEngine.HeapEntry> ordered = maxHeapEngine.snapshotSortedDescending();
        Map<Long, Ticket> byId = ticketRepository.findAllById(
                ordered.stream().map(e -> e.ticketId).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Ticket::getId, t -> t));

        List<Ticket> result = new ArrayList<>();
        for (MaxHeapEngine.HeapEntry e : ordered) {
            Ticket t = byId.get(e.ticketId);
            if (t != null) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Returns a paginated view of non-resolved tickets sorted by live heap priority.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Ticket> getTicketsSortedByPriority(org.springframework.data.domain.Pageable pageable) {
        List<MaxHeapEngine.HeapEntry> ordered = maxHeapEngine.snapshotSortedDescending();
        List<Long> ids = ordered.stream().map(e -> e.ticketId).collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), ids.size());

        if (start > ids.size()) {
            return new org.springframework.data.domain.PageImpl<>(new ArrayList<>(), pageable, ids.size());
        }

        List<Long> subListIds = ids.subList(start, end);
        Map<Long, Ticket> byId = ticketRepository.findAllById(subListIds)
                .stream().collect(Collectors.toMap(Ticket::getId, t -> t));

        List<Ticket> pageContent = new ArrayList<>();
        for (Long id : subListIds) {
            Ticket t = byId.get(id);
            if (t != null) {
                pageContent.add(t);
            }
        }

        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, ids.size());
    }


    @Transactional
    public Ticket updateStatus(Long ticketId, String newStatus, String changedByName) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        String oldStatus = ticket.getStatus();

        // Idempotency guard: reject if status is unchanged
        if (oldStatus.equals(newStatus)) {
            return ticket;
        }

        // Workflow transition validation
        validateStatusTransition(oldStatus, newStatus);

        ticket.setStatus(newStatus);
        ticket.setPriorityWeight(computePriorityWeight(ticket));

        boolean isTerminalNow = AppConstants.STATUS_RESOLVED.equals(newStatus)
                || AppConstants.STATUS_CLOSED.equals(newStatus);

        // First response tracking: moving to In Progress counts as first response
        if (AppConstants.STATUS_IN_PROGRESS.equals(newStatus) && ticket.getFirstRespondedAt() == null) {
            ticket.setFirstRespondedAt(LocalDateTime.now());
            if (ticket.getResponseSlaDeadline() != null
                    && LocalDateTime.now().isAfter(ticket.getResponseSlaDeadline())) {
                ticket.setSlaStatus(AppConstants.SLA_STATUS_BREACHED);
            }
        }

        // Freeze SLA when ticket is resolved or closed
        if (isTerminalNow) {
            ticket.setResolvedAt(LocalDateTime.now());
            if (!AppConstants.SLA_STATUS_BREACHED.equals(ticket.getSlaStatus())) {
                if (ticket.getResolutionSlaDeadline() != null
                        && LocalDateTime.now().isAfter(ticket.getResolutionSlaDeadline())) {
                    ticket.setSlaStatus(AppConstants.SLA_STATUS_BREACHED);
                } else {
                    ticket.setSlaStatus(AppConstants.SLA_STATUS_IN_SLA);
                }
            }
        } else {
            recalculateSlaStatus(ticket);
        }

        Ticket saved = ticketRepository.save(ticket);

        if (isTerminalNow) {
            maxHeapEngine.remove(saved.getId());
        } else {
            maxHeapEngine.increaseKey(saved.getId(), saved.getPriorityWeight());
        }

        log.info("Ticket status updated. Ticket ID: {}, Old Status: {}, New Status: {}, Changed By: {}",
                saved.getId(), oldStatus, newStatus, changedByName);
        logActivity(saved.getId(), "STATUS_CHANGED:" + oldStatus + "->" + newStatus, changedByName);
        return saved;
    }

    /**
     * Validates that a status transition follows the allowed workflow.
     * Open -> Assigned -> In Progress -> Resolved -> Closed
     * Throws IllegalArgumentException for invalid transitions.
     */
    private void validateStatusTransition(String current, String next) {
        java.util.Set<String> allowed = new java.util.HashSet<>();
        switch (current) {
            case "Open"         -> allowed.addAll(java.util.List.of("Assigned", "In Progress"));
            case "Assigned"     -> allowed.addAll(java.util.List.of("In Progress", "Open"));
            case "In Progress"  -> allowed.addAll(java.util.List.of("Resolved"));
            case "Resolved"     -> allowed.addAll(java.util.List.of("Closed"));
            case "Closed"       -> { /* terminal — no transitions allowed */ }
            default             -> allowed.addAll(java.util.List.of("Open", "Assigned", "In Progress", "Resolved", "Closed"));
        }
        if (!allowed.contains(next)) {
            throw new IllegalArgumentException(
                    "Invalid status transition: " + current + " -> " + next +
                    ". Allowed transitions: " + allowed);
        }
    }

    @Transactional
    public Ticket assignTicket(Long ticketId, String assignee, String changedByName) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        // Prevent assigning a terminal ticket
        if (AppConstants.STATUS_RESOLVED.equals(ticket.getStatus())
                || AppConstants.STATUS_CLOSED.equals(ticket.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot assign a ticket with status: " + ticket.getStatus());
        }

        // Idempotency: skip if same assignee and already assigned
        if (assignee != null && assignee.equals(ticket.getAssignedTo())) {
            return ticket;
        }

        ticket.setAssignedTo(assignee);

        // Auto-transition Open -> Assigned
        if (AppConstants.STATUS_OPEN.equals(ticket.getStatus())) {
            ticket.setStatus(AppConstants.STATUS_ASSIGNED);
            logActivity(ticketId, "STATUS_CHANGED:Open->Assigned", changedByName);
        }

        if (ticket.getAssignedAt() == null) {
            ticket.setAssignedAt(LocalDateTime.now());
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket assigned. Ticket ID: {}, Assignee: {}, Changed By: {}",
                saved.getId(), assignee, changedByName);
        logActivity(saved.getId(), "ASSIGNED:" + assignee, changedByName);
        return saved;
    }

    @Transactional
    public Comment addComment(Long ticketId, String authorName, String text) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket != null) {
            boolean isTerminal = AppConstants.STATUS_RESOLVED.equals(ticket.getStatus())
                    || "Closed".equals(ticket.getStatus());
            // Adding a comment is treated as a first response (by staff or admin)
            if (!isTerminal && ticket.getFirstRespondedAt() == null) {
                ticket.setFirstRespondedAt(LocalDateTime.now());
                // Check if response SLA was breached when first comment was added
                if (ticket.getResponseSlaDeadline() != null
                        && LocalDateTime.now().isAfter(ticket.getResponseSlaDeadline())) {
                    ticket.setSlaStatus(AppConstants.SLA_STATUS_BREACHED);
                }
                ticketRepository.save(ticket);
            }
        }

        Comment comment = new Comment();
        comment.setTicketId(ticketId);
        comment.setAuthorName(authorName);
        comment.setText(text);
        comment.setCreatedAt(LocalDateTime.now());
        return commentRepository.save(comment);
    }

    private void logActivity(Long ticketId, String action, String changedByName) {
        activityHistoryRepository.save(
                new ActivityHistory(ticketId, action, changedByName, LocalDateTime.now())
        );
    }

    /**
     * Derives the Max-Heap ordering key from ticket priority + closeness to SLA deadline.
     */
    private int computePriorityWeight(Ticket ticket) {
        int base;
        switch (ticket.getPriority() == null ? AppConstants.PRIORITY_LOW : ticket.getPriority()) {
            case AppConstants.PRIORITY_CRITICAL -> base = 400;
            case AppConstants.PRIORITY_HIGH     -> base = 300;
            case AppConstants.PRIORITY_MEDIUM   -> base = 200;
            default                             -> base = 100;
        }

        // Escalate priority weight based on resolution SLA deadline (not just generic dueDate)
        LocalDateTime deadline = ticket.getResolutionSlaDeadline() != null
                ? ticket.getResolutionSlaDeadline()
                : ticket.getDueDate();

        if (deadline != null) {
            Duration untilDue = Duration.between(LocalDateTime.now(), deadline);
            if (untilDue.isNegative()) {
                base += 100; // overdue / breached
            } else if (untilDue.toHours() <= 4) {
                base += 50;  // due within 4 hours
            } else if (untilDue.toHours() <= 24) {
                base += 20;  // due within a day
            }
        }
        return base;
    }

    /**
     * Background escalation sweep — runs every 60 seconds.
     * 1. Recomputes priority weights for in-memory heap ordering.
     * 2. Recalculates SLA status for all active (non-resolved, non-closed) tickets.
     * Resolved and closed tickets are skipped — their SLA is frozen.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void escalationSweep() {
        // Exclude both Resolved and Closed from SLA recalculation
        List<Ticket> active = ticketRepository.findByStatusNotIn(
                List.of(AppConstants.STATUS_RESOLVED, AppConstants.STATUS_CLOSED)
        );
        for (Ticket ticket : active) {
            int recomputed = computePriorityWeight(ticket);
            if (recomputed != ticket.getPriorityWeight()) {
                ticket.setPriorityWeight(recomputed);
            }
            String oldSlaStatus = ticket.getSlaStatus();
            recalculateSlaStatus(ticket);
            if (recomputed != ticket.getPriorityWeight()
                    || !Objects.equals(oldSlaStatus, ticket.getSlaStatus())) {
                ticketRepository.save(ticket);
            }
            maxHeapEngine.increaseKey(ticket.getId(), recomputed);
        }
    }
}

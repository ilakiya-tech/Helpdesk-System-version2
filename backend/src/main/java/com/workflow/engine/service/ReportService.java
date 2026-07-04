package com.workflow.engine.service;

import com.workflow.engine.config.AppConstants;
import com.workflow.engine.entity.Ticket;
import com.workflow.engine.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes aggregate ticket-state and SLA metrics for operational dashboards and reports.
 * remainingTime is never persisted — it is computed dynamically via Ticket.getRemainingTime().
 */
@Service
public class ReportService {

    private final TicketRepository ticketRepository;

    public ReportService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStatusSummary() {
        List<Ticket> all = ticketRepository.findAll();
        long total = all.size();

        // ── Status counts ──────────────────────────────────────────────────────
        long open       = all.stream().filter(t -> AppConstants.STATUS_OPEN.equals(t.getStatus())).count();
        long inProgress = all.stream().filter(t -> AppConstants.STATUS_IN_PROGRESS.equals(t.getStatus())).count();
        long resolved   = all.stream().filter(t -> AppConstants.STATUS_RESOLVED.equals(t.getStatus())).count();

        // ── SLA distribution (slaStatus stored per ticket by the scheduler) ──
        long inSla    = ticketRepository.countBySlaStatus(AppConstants.SLA_STATUS_IN_SLA);
        long atRisk   = ticketRepository.countBySlaStatus(AppConstants.SLA_STATUS_AT_RISK);
        long breached = ticketRepository.countBySlaStatus(AppConstants.SLA_STATUS_BREACHED);

        // Resolved Within SLA: resolved tickets whose frozen slaStatus != BREACHED
        long resolvedWithinSla = all.stream()
                .filter(t -> AppConstants.STATUS_RESOLVED.equals(t.getStatus()))
                .filter(t -> !AppConstants.SLA_STATUS_BREACHED.equals(t.getSlaStatus()))
                .count();

        // ── Average Response Time (only tickets with a first response) ────────
        OptionalDouble avgResponseOpt = all.stream()
                .filter(t -> t.getFirstRespondedAt() != null && t.getCreatedAt() != null)
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getFirstRespondedAt()).toMinutes())
                .average();
        Long avgResponseMinutes = avgResponseOpt.isPresent() ? (long) avgResponseOpt.getAsDouble() : null;

        // ── Average Resolution Time (only resolved tickets) ───────────────────
        OptionalDouble avgResolutionOpt = all.stream()
                .filter(t -> AppConstants.STATUS_RESOLVED.equals(t.getStatus())
                        && t.getResolvedAt() != null && t.getCreatedAt() != null)
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes())
                .average();
        Long avgResolutionMinutes = avgResolutionOpt.isPresent() ? (long) avgResolutionOpt.getAsDouble() : null;

        // ── Response SLA Met / Missed ─────────────────────────────────────────
        List<Ticket> respondedTickets = all.stream()
                .filter(t -> t.getFirstRespondedAt() != null && t.getResponseSlaDeadline() != null)
                .collect(Collectors.toList());
        long responseSlaMetCount    = respondedTickets.stream()
                .filter(t -> !t.getFirstRespondedAt().isAfter(t.getResponseSlaDeadline()))
                .count();
        long responseSla_missed     = respondedTickets.size() - responseSlaMetCount;

        // ── Resolution SLA Met / Missed ───────────────────────────────────────
        List<Ticket> resolvedTickets = all.stream()
                .filter(t -> AppConstants.STATUS_RESOLVED.equals(t.getStatus())
                        && t.getResolvedAt() != null && t.getResolutionSlaDeadline() != null)
                .collect(Collectors.toList());
        long resolutionSlaMetCount  = resolvedTickets.stream()
                .filter(t -> !t.getResolvedAt().isAfter(t.getResolutionSlaDeadline()))
                .count();
        long resolutionSla_missed   = resolvedTickets.size() - resolutionSlaMetCount;

        // ── SLA Compliance % ──────────────────────────────────────────────────
        double compliancePercent = total == 0 ? 100.0
                : ((double)(total - breached) / total) * 100.0;

        // ── Tickets by Priority ───────────────────────────────────────────────
        Map<String, Long> byPriority = new LinkedHashMap<>();
        byPriority.put("Critical", all.stream().filter(t -> "Critical".equals(t.getPriority())).count());
        byPriority.put("High",     all.stream().filter(t -> "High".equals(t.getPriority())).count());
        byPriority.put("Medium",   all.stream().filter(t -> "Medium".equals(t.getPriority())).count());
        byPriority.put("Low",      all.stream().filter(t -> "Low".equals(t.getPriority())).count());

        // ── Build summary map ─────────────────────────────────────────────────
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("open",        open);
        summary.put("inProgress",  inProgress);
        summary.put("resolved",    resolved);
        summary.put("total",       total);
        summary.put("inSla",             inSla);
        summary.put("atRisk",            atRisk);
        summary.put("breached",          breached);
        summary.put("resolvedWithinSla", resolvedWithinSla);
        summary.put("avgResponseMinutes",   avgResponseMinutes);
        summary.put("avgResolutionMinutes", avgResolutionMinutes);
        summary.put("responseSlaMetCount",    responseSlaMetCount);
        summary.put("responseSla_missed",     responseSla_missed);
        summary.put("resolutionSlaMetCount",  resolutionSlaMetCount);
        summary.put("resolutionSla_missed",   resolutionSla_missed);
        summary.put("slaCompliancePercent",   Math.round(compliancePercent * 10.0) / 10.0);
        summary.put("ticketsByPriority",      byPriority);

        return summary;
    }
}

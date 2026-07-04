package com.workflow.engine.service;

import com.workflow.engine.config.AppConstants;
import com.workflow.engine.config.SlaProperties;
import com.workflow.engine.entity.Ticket;
import com.workflow.engine.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TicketServiceTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    public void setUp() {
        ticketRepository.deleteAll();
    }

    @Test
    public void testTicketCreationAndSlaDeadline() {
        Ticket ticket = new Ticket();
        ticket.setTitle("Critical Database Outage");
        ticket.setPriority(AppConstants.PRIORITY_CRITICAL);
        ticket.setStatus(AppConstants.STATUS_OPEN);
        ticket.setDescription("Production DB is not responding.");

        Ticket saved = ticketService.createTicket(ticket, "TestAdmin");

        assertNotNull(saved.getId());
        assertNotNull(saved.getResponseSlaDeadline());
        assertNotNull(saved.getResolutionSlaDeadline());
        assertEquals(AppConstants.SLA_STATUS_IN_SLA, saved.getSlaStatus());

        // For Critical: response deadline is 30 mins, resolution deadline is 240 mins (4 hours)
        long responseDiff = java.time.Duration.between(saved.getCreatedAt(), saved.getResponseSlaDeadline()).toMinutes();
        long resolutionDiff = java.time.Duration.between(saved.getCreatedAt(), saved.getResolutionSlaDeadline()).toMinutes();

        assertEquals(30, responseDiff);
        assertEquals(240, resolutionDiff);
    }

    @Test
    public void testFirstResponseTrackingAndSlaBreach() {
        Ticket ticket = new Ticket();
        ticket.setTitle("Low priority ticket");
        ticket.setPriority(AppConstants.PRIORITY_LOW);
        ticket.setStatus(AppConstants.STATUS_OPEN);

        Ticket saved = ticketService.createTicket(ticket, "Customer");
        assertNull(saved.getFirstRespondedAt());

        // Update status to In Progress to record first response
        Ticket updated = ticketService.updateStatus(saved.getId(), AppConstants.STATUS_IN_PROGRESS, "StaffUser");

        assertNotNull(updated.getFirstRespondedAt());
        assertTrue(updated.getFirstRespondedAt().isAfter(saved.getCreatedAt()) || updated.getFirstRespondedAt().isEqual(saved.getCreatedAt()));
    }

    @Test
    public void testTicketAssignment() {
        Ticket ticket = new Ticket();
        ticket.setTitle("High priority ticket");
        ticket.setPriority(AppConstants.PRIORITY_HIGH);
        ticket.setStatus(AppConstants.STATUS_OPEN);

        Ticket saved = ticketService.createTicket(ticket, "Customer");
        assertNull(saved.getAssignedTo());
        assertNull(saved.getAssignedAt());

        Ticket assigned = ticketService.assignTicket(saved.getId(), "StaffJohn", "AdminAlice");

        assertEquals("StaffJohn", assigned.getAssignedTo());
        assertNotNull(assigned.getAssignedAt());
    }

    @Test
    public void testEscalationSweepUpdatesStatusesAndWeights() {
        Ticket ticket = new Ticket();
        ticket.setTitle("Immediate Breach Ticket");
        ticket.setPriority(AppConstants.PRIORITY_CRITICAL);
        ticket.setStatus(AppConstants.STATUS_OPEN);

        Ticket saved = ticketService.createTicket(ticket, "Customer");

        // Backdate SLA deadlines to simulate breach
        saved.setResponseSlaDeadline(LocalDateTime.now().minusMinutes(5));
        saved.setResolutionSlaDeadline(LocalDateTime.now().minusMinutes(10));
        ticketRepository.saveAndFlush(saved);

        // Run sweep manually
        ticketService.escalationSweep();

        Ticket swept = ticketRepository.findById(saved.getId()).orElseThrow();
        assertEquals(AppConstants.SLA_STATUS_BREACHED, swept.getSlaStatus());
    }
}

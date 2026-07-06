package com.workflow.engine.service;

import com.workflow.engine.config.AppConstants;
import com.workflow.engine.entity.Ticket;
import com.workflow.engine.entity.User;
import com.workflow.engine.repository.TicketRepository;
import com.workflow.engine.repository.UserRepository;
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

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    public void setUp() {
        ticketRepository.deleteAll();
    }

    @Test
    public void testTicketCreation() {
        Ticket ticket = new Ticket();
        ticket.setTitle("Critical Database Outage");
        ticket.setPriority(AppConstants.PRIORITY_CRITICAL);
        ticket.setStatus(AppConstants.STATUS_OPEN);
        ticket.setDescription("Production DB is not responding.");

        Ticket saved = ticketService.createTicket(ticket, "TestAdmin");

        assertNotNull(saved.getId());
        assertEquals("Critical Database Outage", saved.getTitle());
        assertEquals(AppConstants.PRIORITY_CRITICAL, saved.getPriority());
    }

    @Test
    public void testStatusTracking() {
        Ticket ticket = new Ticket();
        ticket.setTitle("Low priority ticket");
        ticket.setPriority(AppConstants.PRIORITY_LOW);
        ticket.setStatus(AppConstants.STATUS_OPEN);

        Ticket saved = ticketService.createTicket(ticket, "Customer");
        assertEquals(AppConstants.STATUS_OPEN, saved.getStatus());

        // Update status to In Progress
        Ticket updated = ticketService.updateStatus(saved.getId(), AppConstants.STATUS_IN_PROGRESS, "StaffUser");
        assertEquals(AppConstants.STATUS_IN_PROGRESS, updated.getStatus());
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
    public void testAssignTicketToUnavailableStaffThrowsException() {
        // Create an unavailable staff user
        User staff = new User();
        staff.setUsername("StaffUnavailable");
        staff.setPassword("password");
        staff.setRole("staff");
        staff.setName("Unavailable Staff");
        staff.setAvailability("on_leave");
        userRepository.save(staff);

        Ticket ticket = new Ticket();
        ticket.setTitle("Test Ticket");
        ticket.setPriority(AppConstants.PRIORITY_MEDIUM);
        ticket.setStatus(AppConstants.STATUS_OPEN);
        Ticket saved = ticketService.createTicket(ticket, "Customer");

        // Try assigning to the unavailable staff member and assert exception is thrown
        assertThrows(IllegalArgumentException.class, () -> {
            ticketService.assignTicket(saved.getId(), "StaffUnavailable", "AdminAlice");
        });
    }
}

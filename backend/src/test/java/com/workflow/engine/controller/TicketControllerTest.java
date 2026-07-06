package com.workflow.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.config.AppConstants;
import com.workflow.engine.entity.Ticket;
import com.workflow.engine.entity.User;
import java.time.LocalDateTime;
import com.workflow.engine.repository.TicketRepository;
import com.workflow.engine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    public void setUp() {
        ticketRepository.deleteAll();
        userRepository.deleteAll();

        // Seed users
        User admin = new User();
        admin.setUsername("admin_user");
        admin.setPassword("password");
        admin.setRole("admin");
        admin.setName("Admin Admin");
        userRepository.save(admin);

        User staff = new User();
        staff.setUsername("staff_user");
        staff.setPassword("password");
        staff.setRole("staff");
        staff.setName("Staff User");
        userRepository.save(staff);

        User consumer = new User();
        consumer.setUsername("consumer_user");
        consumer.setPassword("password");
        consumer.setRole("consumer");
        consumer.setName("Consumer User");
        consumer.setEmail("consumer@example.com");
        consumer.setMobile("1234567890");
        userRepository.save(consumer);

        // Seed tickets
        Ticket t1 = new Ticket();
        t1.setTitle("Admin assigned ticket");
        t1.setDescription("SLA Issue");
        t1.setCustomerName("Consumer User");
        t1.setCreatedByName("consumer_user");
        t1.setAssignedTo("staff_user");
        t1.setStatus(AppConstants.STATUS_OPEN);
        t1.setPriority("Low");
        t1.setCreatedAt(LocalDateTime.now());
        ticketRepository.save(t1);

        Ticket t2 = new Ticket();
        t2.setTitle("Unassigned other ticket");
        t2.setDescription("Other customer issue");
        t2.setCustomerName("Someone Else");
        t2.setCreatedByName("other_user");
        t2.setStatus(AppConstants.STATUS_OPEN);
        t2.setPriority("Medium");
        t2.setCreatedAt(LocalDateTime.now());
        ticketRepository.save(t2);
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    public void testAdminSeesAllTickets() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "staff_user", roles = {"STAFF"})
    public void testStaffSeesOnlyAssignedTickets() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Admin assigned ticket"));
    }

    @Test
    @WithMockUser(username = "consumer_user", roles = {"CONSUMER"})
    public void testConsumerSeesOnlyOwnTickets() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Admin assigned ticket"));
    }

    @Test
    @WithMockUser(username = "consumer_user", roles = {"CONSUMER"})
    public void testConsumerCannotAccessOtherTicketDetails() throws Exception {
        Ticket otherTicket = ticketRepository.findAll().stream()
                .filter(t -> "other_user".equals(t.getCreatedByName()))
                .findFirst().orElseThrow();

        mockMvc.perform(get("/api/tickets/" + otherTicket.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "consumer_user", roles = {"CONSUMER"})
    public void testConsumerCanAccessOwnTicketDetails() throws Exception {
        Ticket ownTicket = ticketRepository.findAll().stream()
                .filter(t -> "consumer_user".equals(t.getCreatedByName()))
                .findFirst().orElseThrow();

        mockMvc.perform(get("/api/tickets/" + ownTicket.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.title").value("Admin assigned ticket"));
    }
}

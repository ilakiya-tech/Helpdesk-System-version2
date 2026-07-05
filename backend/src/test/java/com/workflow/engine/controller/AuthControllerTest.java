package com.workflow.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.dto.LoginRequest;
import com.workflow.engine.dto.RegisterRequest;
import com.workflow.engine.entity.User;
import com.workflow.engine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.org.secret}")
    private String adminSecretKey;

    @BeforeEach
    public void setUp() {
        userRepository.deleteAll();
    }

    @Test
    public void testUserRegistrationSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "john_doe",
                "password123",
                "client",
                "John Doe",
                "john@example.com",
                "1234567890",
                "IT",
                "Available",
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    public void testAdminRegistrationWithValidSecretKey() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "admin_user",
                "password123",
                "admin",
                "Admin User",
                "admin@example.com",
                "0987654321",
                "Management",
                "Available",
                adminSecretKey,
                null,
                null
        );

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    public void testAdminRegistrationWithInvalidSecretKeyReturnsForbidden() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "admin_user_fail",
                "password123",
                "admin",
                "Admin User",
                "admin@example.com",
                "0987654321",
                "Management",
                "Available",
                "wrong-secret",
                null,
                null
        );

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Forbidden: Invalid Organization Secret Key"));
    }

    @Test
    public void testUserLoginSuccess() throws Exception {
        // First register a user
        RegisterRequest register = new RegisterRequest(
                "jane_doe",
                "password123",
                "client",
                "Jane Doe",
                "jane@example.com",
                "1234567890",
                "Support",
                "Available",
                null,
                null,
                null
        );
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        // Attempt login
        LoginRequest login = new LoginRequest("jane_doe", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    public void testAccessProtectedEndpointWithoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }
}

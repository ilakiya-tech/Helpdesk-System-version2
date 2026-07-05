package com.workflow.engine.controller;

import com.workflow.engine.config.AppConstants;
import com.workflow.engine.dto.LoginRequest;
import com.workflow.engine.dto.RegisterRequest;
import com.workflow.engine.entity.User;
import com.workflow.engine.repository.UserRepository;
import com.workflow.engine.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@Tag(name = "Authentication & Onboarding", description = "Endpoints for user registration and JWT authentication.")
public class AuthController {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final com.workflow.engine.service.EmailService emailService;

    @Value("${app.org.secret}")
    private String adminSecretKey;

    public AuthController(UserRepository userRepository,
                          AuthenticationManager authenticationManager,
                          JwtTokenProvider tokenProvider,
                          PasswordEncoder passwordEncoder,
                          com.workflow.engine.service.EmailService emailService) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @PostMapping("/auth/login")
    @Operation(
        summary = "User Login",
        description = "Authenticates credentials and issues a JWT token. Open endpoint."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
        @ApiResponse(responseCode = "401", description = "Invalid username or password")
    })
    public ResponseEntity<Map<String, Object>> login(@RequestBody @Valid LoginRequest request) {
        String username = request.username() != null ? request.username().trim() : "";
        String password = request.password() != null ? request.password().trim() : "";

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        Optional<User> userOpt = userRepository.findByUsername(username);
        User user = userOpt.get();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success",  true);
        response.put("token",    jwt);
        response.put("userRole", user.getRole());
        response.put("username", user.getUsername());
        response.put("name",     user.getName());
        response.put("userId",   user.getId());
        response.put("email",    user.getEmail() != null ? user.getEmail() : "");
        response.put("mobile",   user.getMobile() != null ? user.getMobile() : "");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/register")
    @Operation(
        summary = "Register user account",
        description = "Creates a new user profile. Admin roles require organization secret key validation. Open endpoint."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Validation parameters failed"),
        @ApiResponse(responseCode = "403", description = "Forbidden: Invalid secret key for admin"),
        @ApiResponse(responseCode = "409", description = "Conflict: Username already taken")
    })
    public ResponseEntity<Map<String, Object>> register(@RequestBody @Valid RegisterRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        String trimmedUsername = request.username() != null ? request.username().trim() : "";
        String trimmedPassword = request.password() != null ? request.password().trim() : "";
        String trimmedEmail = request.email() != null ? request.email().trim() : "";

        if (userRepository.existsByUsername(trimmedUsername)) {
            response.put("success", false);
            response.put("message", "Username already taken");
            return ResponseEntity.status(409).body(response);
        }

        // Check for duplicate email (only if email is provided)
        if (!trimmedEmail.isBlank() && userRepository.existsByEmail(trimmedEmail)) {
            response.put("success", false);
            response.put("message", "Email address is already registered");
            return ResponseEntity.status(409).body(response);
        }

        String role = request.role() == null ? AppConstants.DEFAULT_ROLE : request.role().toLowerCase();

        // Validate admin registration
        if (AppConstants.ROLE_ADMIN.equals(role)) {
            if (request.secretKey() == null || !request.secretKey().equals(adminSecretKey)) {
                response.put("success", false);
                response.put("message", "Forbidden: Invalid Organization Secret Key");
                return ResponseEntity.status(403).body(response);
            }
        }

        User user = new User(
                trimmedUsername,
                passwordEncoder.encode(trimmedPassword), // Hashed password
                role,
                request.name() != null ? request.name().trim() : "",
                trimmedEmail,
                request.mobile() != null ? request.mobile().trim() : "",
                request.department() != null ? request.department().trim() : "",
                request.availability()
        );
        user.setDesignation(request.designation());
        user.setSpecialization(request.specialization());

        User saved = userRepository.save(user);

        if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
            emailService.sendWelcomeEmail(saved.getEmail(), saved.getName(), saved.getRole());
        }

        // We do not auto-login upon registration to maintain explicit login flow
        response.put("success",  true);
        response.put("message", "User registered successfully");
        response.put("userRole", saved.getRole());
        response.put("username", saved.getUsername());
        response.put("name",     saved.getName());
        response.put("userId",   saved.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/forgot-password")
    @Operation(summary = "Request password reset OTP", description = "Generates and sends a 6-digit OTP code to the user's email.")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        if (username != null) username = username.trim();
        Map<String, Object> response = new LinkedHashMap<>();

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Username not found");
            return ResponseEntity.status(404).body(response);
        }

        User user = userOpt.get();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            response.put("success", false);
            response.put("message", "No email registered for this user");
            return ResponseEntity.status(400).body(response);
        }

        // Generate 6 digit OTP
        String otp = String.format("%06d", new java.util.Random().nextInt(1000000));
        user.setOtpCode(otp);
        user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), user.getName(), otp);

        response.put("success", true);
        response.put("message", "OTP has been sent to your registered email");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/reset-password")
    @Operation(summary = "Reset password using OTP", description = "Verifies the OTP code and updates the password.")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        if (username != null) username = username.trim();
        String otp = payload.get("otp");
        if (otp != null) otp = otp.trim();
        String newPassword = payload.get("newPassword");
        Map<String, Object> response = new LinkedHashMap<>();

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Username not found");
            return ResponseEntity.status(404).body(response);
        }

        User user = userOpt.get();
        if (user.getOtpCode() == null || !user.getOtpCode().equals(otp) ||
                user.getOtpExpiry() == null || user.getOtpExpiry().isBefore(java.time.LocalDateTime.now())) {
            response.put("success", false);
            response.put("message", "Invalid or expired OTP");
            return ResponseEntity.status(400).body(response);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        response.put("success", true);
        response.put("message", "Password reset successfully");
        return ResponseEntity.ok(response);
    }
}

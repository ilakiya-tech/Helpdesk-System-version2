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

    @Value("${app.org.secret}")
    private String adminSecretKey;

    public AuthController(UserRepository userRepository,
                          AuthenticationManager authenticationManager,
                          JwtTokenProvider tokenProvider,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
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
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        Optional<User> userOpt = userRepository.findByUsername(request.username());
        User user = userOpt.get();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success",  true);
        response.put("token",    jwt);
        response.put("userRole", user.getRole());
        response.put("username", user.getUsername());
        response.put("name",     user.getName());
        response.put("userId",   user.getId());
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

        if (userRepository.existsByUsername(request.username())) {
            response.put("success", false);
            response.put("message", "Username already taken");
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
                request.username(),
                passwordEncoder.encode(request.password()), // Hashed password
                role,
                request.name(),
                request.email(),
                request.mobile(),
                request.department(),
                request.availability()
        );
        User saved = userRepository.save(user);

        // We do not auto-login upon registration to maintain explicit login flow
        response.put("success",  true);
        response.put("message", "User registered successfully");
        response.put("userRole", saved.getRole());
        response.put("username", saved.getUsername());
        response.put("name",     saved.getName());
        response.put("userId",   saved.getId());
        return ResponseEntity.ok(response);
    }
}

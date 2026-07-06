package com.workflow.engine.controller;

import com.workflow.engine.dsa.TrieEngine;
import com.workflow.engine.entity.Ticket;
import com.workflow.engine.entity.User;
import com.workflow.engine.repository.TicketRepository;
import com.workflow.engine.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Real-time autocomplete backed entirely by the in-memory TrieEngine -
 * O(p + m) per request (p = prefix length, m = matches), no DB hit on
 * the hot path. The matching ticketIds are then resolved to full Ticket
 * rows via a single batched findAllById call.
 */
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Ticket Search", description = "Endpoints for real-time autocomplete search using Trie engine.")
public class SearchController {

    private final TrieEngine trieEngine;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public SearchController(TrieEngine trieEngine, TicketRepository ticketRepository, UserRepository userRepository) {
        this.trieEngine = trieEngine;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(
        summary = "Autocomplete search by prefix",
        description = "Performs instant prefix matching against ticket titles using the in-memory Trie index, then resolves matching keys via batched DB lookup, filtered by role access.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved matched tickets"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header")
    })
    public ResponseEntity<List<Ticket>> searchByPrefix(@RequestParam String prefix) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        User user = userOpt.get();

        Set<Long> matchingIds = trieEngine.searchByPrefix(prefix);
        List<Ticket> allMatches = ticketRepository.findAllById(matchingIds);

        List<Ticket> filtered;
        if ("admin".equalsIgnoreCase(user.getRole())) {
            filtered = allMatches;
        } else if ("staff".equalsIgnoreCase(user.getRole())) {
            filtered = allMatches.stream()
                    .filter(t -> user.getUsername().equalsIgnoreCase(t.getAssignedTo()) || 
                                 user.getName().equalsIgnoreCase(t.getAssignedTo()))
                    .collect(Collectors.toList());
        } else {
            // Consumer
            filtered = allMatches.stream()
                    .filter(t -> user.getUsername().equalsIgnoreCase(t.getCreatedByName()) ||
                                 user.getUsername().equalsIgnoreCase(t.getCustomerName()) || 
                                 user.getName().equalsIgnoreCase(t.getCustomerName()) ||
                                 (t.getEmail() != null && t.getEmail().equalsIgnoreCase(user.getEmail())) ||
                                 (t.getMobile() != null && t.getMobile().equalsIgnoreCase(user.getMobile()))
                    )
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(filtered);
    }
}

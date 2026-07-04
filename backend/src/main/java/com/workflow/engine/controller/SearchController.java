package com.workflow.engine.controller;

import com.workflow.engine.dsa.TrieEngine;
import com.workflow.engine.entity.Ticket;
import com.workflow.engine.repository.TicketRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    public SearchController(TrieEngine trieEngine, TicketRepository ticketRepository) {
        this.trieEngine = trieEngine;
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/search")
    @Operation(
        summary = "Autocomplete search by prefix",
        description = "Performs instant prefix matching against ticket titles using the in-memory Trie index, then resolves matching keys via batched DB lookup.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved matched tickets"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header")
    })
    public ResponseEntity<List<Ticket>> searchByPrefix(@RequestParam String prefix) {
        Set<Long> matchingIds = trieEngine.searchByPrefix(prefix);
        List<Ticket> results = ticketRepository.findAllById(matchingIds)
                 .stream()
                 .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }
}

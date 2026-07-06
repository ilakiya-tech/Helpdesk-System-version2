package com.workflow.engine.controller;

import com.workflow.engine.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reporting & Analytics", description = "Endpoints for retrieving SLA metrics and ticket summaries.")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Get helpdesk and ticket summary reports",
        description = "Returns aggregate counts and distributions (status, priority, department, monthly trend, and staff workload) for the Admin dashboard.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved report summary"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid authorization header"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN role)")
    })
    public ResponseEntity<Map<String, Object>> getStatusSummary() {
        return ResponseEntity.ok(reportService.getStatusSummary());
    }
}


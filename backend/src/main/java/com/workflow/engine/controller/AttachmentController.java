package com.workflow.engine.controller;

import com.workflow.engine.entity.Attachment;
import com.workflow.engine.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Attachment Management", description = "Endpoints for uploading and downloading ticket file attachments.")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/api/tickets/{ticketId}/attachments")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(summary = "Upload attachment to ticket", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> uploadAttachment(
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Attachment attachment = attachmentService.saveAttachment(ticketId, file, username);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "File uploaded successfully",
                    "attachment", attachment
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Error saving attachment: " + e.getMessage()));
        }
    }

    @GetMapping("/api/tickets/{ticketId}/attachments")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(summary = "Get all attachments for a ticket", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<Attachment>> getAttachments(@PathVariable Long ticketId) {
        return ResponseEntity.ok(attachmentService.getAttachmentsByTicket(ticketId));
    }

    @GetMapping("/api/attachments/download/{id}")
    @PreAuthorize("hasAnyRole('CONSUMER', 'STAFF', 'ADMIN')")
    @Operation(summary = "Download ticket attachment file", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> downloadAttachment(@PathVariable Long id) {
        try {
            Attachment attachment = attachmentService.getAttachmentById(id);
            if (attachment == null) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = Paths.get(attachment.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.badRequest().body("Attachment file not found on disk");
            }

            String contentType = attachment.getFileType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error retrieving attachment: " + e.getMessage());
        }
    }
}

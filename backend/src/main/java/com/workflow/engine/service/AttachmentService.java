package com.workflow.engine.service;

import com.workflow.engine.entity.Attachment;
import com.workflow.engine.repository.AttachmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final String UPLOAD_DIR = "uploads";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB limit
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "doc", "docx", "png", "jpg", "jpeg");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/png",
            "image/jpeg"
    );

    private final AttachmentRepository attachmentRepository;

    public AttachmentService(AttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
        // Ensure local uploads directory exists
        try {
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            System.err.println("Could not create uploads directory: " + e.getMessage());
        }
    }

    public Attachment saveAttachment(Long ticketId, MultipartFile file, String uploadedBy) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty file");
        }

        // 1. Validate File Size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the maximum limit of 5MB");
        }

        // 2. Validate File Type / Extension
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new IllegalArgumentException("Invalid filename format");
        }

        String ext = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file extension: " + ext);
        }

        String mimeType = file.getContentType();
        if (mimeType != null && !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("Unsupported MIME type: " + mimeType);
        }

        // 3. Save File Locally
        String uniqueName = UUID.randomUUID().toString() + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path targetPath = Paths.get(UPLOAD_DIR).resolve(uniqueName);
        Files.copy(file.getInputStream(), targetPath);

        // 4. Save Database Record
        Attachment attachment = new Attachment(
                ticketId,
                originalName,
                targetPath.toString(),
                mimeType != null ? mimeType : "application/octet-stream",
                file.getSize(),
                uploadedBy
        );

        return attachmentRepository.save(attachment);
    }

    public List<Attachment> getAttachmentsByTicket(Long ticketId) {
        return attachmentRepository.findByTicketId(ticketId);
    }

    public Attachment getAttachmentById(Long id) {
        return attachmentRepository.findById(id).orElse(null);
    }
}

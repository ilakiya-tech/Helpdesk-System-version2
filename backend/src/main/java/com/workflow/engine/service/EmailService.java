package com.workflow.engine.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String to, String subject, String htmlContent) {
        if (to == null || to.trim().isEmpty() || !to.contains("@")) {
            System.out.println("Invalid email recipient: " + to + ". Skipping send.");
            return;
        }

        // Send asynchronously to avoid blocking the main thread
        executorService.submit(() -> {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
                helper.setText(htmlContent, true);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setFrom("Carbochem Helpdesk <carbochem.helpdesk@gmail.com>");
                mailSender.send(mimeMessage);
                System.out.println("Email sent successfully to: " + to);
            } catch (Exception e) {
                // Graceful error handling - log the issue, but never fail the user request
                System.err.println("Failed to send email to " + to + ": " + e.getMessage());
            }
        });
    }

    public void sendWelcomeEmail(String to, String name, String role) {
        String subject = "Welcome to Carbochem Helpdesk!";
        String content = "<html><body style='font-family: Arial, sans-serif; color: #1e293b;'>" +
                "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>" +
                "<div style='background-color: #1F3373; padding: 20px; text-align: center; color: white;'>" +
                "<h2 style='margin: 0;'>Welcome to Carbochem</h2>" +
                "</div>" +
                "<div style='padding: 24px; line-height: 1.6;'>" +
                "<p>Hello <strong>" + name + "</strong>,</p>" +
                "<p>Your account has been successfully created. You have been assigned the role of <strong>" + role.toUpperCase() + "</strong>.</p>" +
                "<p>You can now log in to access your dashboard and manage tickets.</p>" +
                "<br>" +
                "<p>Best regards,<br>Carbochem Admin Team</p>" +
                "</div>" +
                "</div>" +
                "</body></html>";
        sendEmail(to, subject, content);
    }

    public void sendTicketCreatedEmail(String to, String customerName, Long ticketId, String title, String priority) {
        String subject = "Support Ticket Created - #" + ticketId;
        String content = "<html><body style='font-family: Arial, sans-serif; color: #1e293b;'>" +
                "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>" +
                "<div style='background-color: #1F3373; padding: 20px; text-align: center; color: white;'>" +
                "<h2 style='margin: 0;'>Ticket Created</h2>" +
                "</div>" +
                "<div style='padding: 24px; line-height: 1.6;'>" +
                "<p>Hello <strong>" + customerName + "</strong>,</p>" +
                "<p>Your support ticket has been successfully created in the system.</p>" +
                "<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>" +
                "<tr><td style='padding: 8px 0; font-weight: bold; width: 120px;'>Ticket ID:</td><td style='padding: 8px 0;'>#" + ticketId + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Subject:</td><td style='padding: 8px 0;'>" + title + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Priority:</td><td style='padding: 8px 0;'>" + priority + "</td></tr>" +
                "</table>" +
                "<p>Our support team will review it and follow up with you shortly.</p>" +
                "<br>" +
                "<p>Best regards,<br>Carbochem Support Team</p>" +
                "</div>" +
                "</div>" +
                "</body></html>";
        sendEmail(to, subject, content);
    }

    public void sendTicketAssignedEmail(String to, String staffName, Long ticketId, String title, String priority) {
        String subject = "New Ticket Assigned - #" + ticketId;
        String content = "<html><body style='font-family: Arial, sans-serif; color: #1e293b;'>" +
                "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>" +
                "<div style='background-color: #1F3373; padding: 20px; text-align: center; color: white;'>" +
                "<h2 style='margin: 0;'>Ticket Assigned</h2>" +
                "</div>" +
                "<div style='padding: 24px; line-height: 1.6;'>" +
                "<p>Hello <strong>" + staffName + "</strong>,</p>" +
                "<p>A new support ticket has been assigned to you for resolution.</p>" +
                "<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>" +
                "<tr><td style='padding: 8px 0; font-weight: bold; width: 120px;'>Ticket ID:</td><td style='padding: 8px 0;'>#" + ticketId + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Subject:</td><td style='padding: 8px 0;'>" + title + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Priority:</td><td style='padding: 8px 0;'>" + priority + "</td></tr>" +
                "</table>" +
                "<p>Please review the ticket details and begin working on it as soon as possible.</p>" +
                "<br>" +
                "<p>Best regards,<br>Carbochem Admin Team</p>" +
                "</div>" +
                "</div>" +
                "</body></html>";
        sendEmail(to, subject, content);
    }

    public void sendTicketStatusChangedEmail(String to, String recipientName, Long ticketId, String title, String oldStatus, String newStatus) {
        String subject = "Ticket Status Updated - #" + ticketId;
        String content = "<html><body style='font-family: Arial, sans-serif; color: #1e293b;'>" +
                "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>" +
                "<div style='background-color: #1F3373; padding: 20px; text-align: center; color: white;'>" +
                "<h2 style='margin: 0;'>Ticket Status Update</h2>" +
                "</div>" +
                "<div style='padding: 24px; line-height: 1.6;'>" +
                "<p>Hello <strong>" + recipientName + "</strong>,</p>" +
                "<p>The status of Support Ticket <strong>#" + ticketId + " (" + title + ")</strong> has been updated.</p>" +
                "<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>" +
                "<tr><td style='padding: 8px 0; font-weight: bold; width: 120px;'>Previous Status:</td><td style='padding: 8px 0; color: #64748b;'>" + oldStatus + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Current Status:</td><td style='padding: 8px 0; color: #1F3373; font-weight: bold;'>" + newStatus + "</td></tr>" +
                "</table>" +
                "<br>" +
                "<p>Best regards,<br>Carbochem Support Team</p>" +
                "</div>" +
                "</div>" +
                "</body></html>";
        sendEmail(to, subject, content);
    }

    public void sendTicketResolvedEmail(String to, String customerName, Long ticketId, String title) {
        String subject = "Ticket Resolved - #" + ticketId;
        String content = "<html><body style='font-family: Arial, sans-serif; color: #1e293b;'>" +
                "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>" +
                "<div style='background-color: #166534; padding: 20px; text-align: center; color: white;'>" +
                "<h2 style='margin: 0;'>Ticket Resolved</h2>" +
                "</div>" +
                "<div style='padding: 24px; line-height: 1.6;'>" +
                "<p>Hello <strong>" + customerName + "</strong>,</p>" +
                "<p>Your Support Ticket <strong>#" + ticketId + " (" + title + ")</strong> has been marked as <strong>RESOLVED</strong> by our team.</p>" +
                "<p>If the issue persists, please feel free to comment on the ticket to reopen or contact support.</p>" +
                "<br>" +
                "<p>Best regards,<br>Carbochem Support Team</p>" +
                "</div>" +
                "</div>" +
                "</body></html>";
        sendEmail(to, subject, content);
    }

    public void sendLeaveRequestStatusEmail(String to, String staffName, String status, String type, String start, String end) {
        String subject = "Leave Request " + status;
        String color = "APPROVED".equalsIgnoreCase(status) ? "#166534" : "#dc2626";
        String content = "<html><body style='font-family: Arial, sans-serif; color: #1e293b;'>" +
                "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>" +
                "<div style='background-color: " + color + "; padding: 20px; text-align: center; color: white;'>" +
                "<h2 style='margin: 0;'>Leave Request " + status.toUpperCase() + "</h2>" +
                "</div>" +
                "<div style='padding: 24px; line-height: 1.6;'>" +
                "<p>Hello <strong>" + staffName + "</strong>,</p>" +
                "<p>Your leave request has been reviewed by the administrator.</p>" +
                "<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>" +
                "<tr><td style='padding: 8px 0; font-weight: bold; width: 120px;'>Leave Type:</td><td style='padding: 8px 0;'>" + type + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Duration:</td><td style='padding: 8px 0;'>" + start + " to " + end + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Review Status:</td><td style='padding: 8px 0; color: " + color + "; font-weight: bold;'>" + status.toUpperCase() + "</td></tr>" +
                "</table>" +
                "<br>" +
                "<p>Best regards,<br>Carbochem Admin Team</p>" +
                "</div>" +
                "</div>" +
                "</body></html>";
        sendEmail(to, subject, content);
    }

    public void sendPasswordResetEmail(String to, String name, String tempPassword) {
        String subject = "Password Reset Notification";
        String content = "<html><body style='font-family: Arial, sans-serif; color: #1e293b;'>" +
                "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>" +
                "<div style='background-color: #1F3373; padding: 20px; text-align: center; color: white;'>" +
                "<h2 style='margin: 0;'>Password Reset</h2>" +
                "</div>" +
                "<div style='padding: 24px; line-height: 1.6;'>" +
                "<p>Hello <strong>" + name + "</strong>,</p>" +
                "<p>Your account password has been updated by the administrator.</p>" +
                "<p>Your new login password is: <strong style='font-size: 1.15rem; color: #1F3373; background-color: #f1f5f9; padding: 4px 8px; border-radius: 4px;'>" + tempPassword + "</strong></p>" +
                "<p>Please change your password immediately after logging in for security purposes.</p>" +
                "<br>" +
                "<p>Best regards,<br>Carbochem Support Team</p>" +
                "</div>" +
                "</div>" +
                "</body></html>";
        sendEmail(to, subject, content);
    }

    public void sendOtpEmail(String to, String name, String otp) {
        String subject = "Carbochem Helpdesk - Password Reset OTP";
        String content = "<html><body style='font-family: Arial, sans-serif; color: #1e293b;'>" +
                "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>" +
                "<div style='background-color: #1F3373; padding: 20px; text-align: center; color: white;'>" +
                "<h2 style='margin: 0;'>Password Reset OTP</h2>" +
                "</div>" +
                "<div style='padding: 24px; line-height: 1.6;'>" +
                "<p>Hello <strong>" + name + "</strong>,</p>" +
                "<p>We received a request to reset your password. Use the following One-Time Password (OTP) to proceed:</p>" +
                "<p style='text-align: center;'><strong style='font-size: 2rem; color: #1F3373; letter-spacing: 5px; background-color: #f1f5f9; padding: 8px 16px; border-radius: 4px; display: inline-block;'>" + otp + "</strong></p>" +
                "<p>This OTP is valid for 10 minutes. If you did not request a password reset, you can safely ignore this email.</p>" +
                "<br>" +
                "<p>Best regards,<br>Carbochem Support Team</p>" +
                "</div>" +
                "</div>" +
                "</body></html>";
        sendEmail(to, subject, content);
    }
}


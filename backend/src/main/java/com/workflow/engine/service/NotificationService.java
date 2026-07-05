package com.workflow.engine.service;

import com.workflow.engine.entity.Notification;
import com.workflow.engine.entity.User;
import com.workflow.engine.repository.NotificationRepository;
import com.workflow.engine.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public void createNotification(Long userId, String title, String message, String type, Long referenceId) {
        Notification n = new Notification(userId, title, message, type, referenceId);
        notificationRepository.save(n);
    }

    public void notifyAdmins(String title, String message, String type, Long referenceId) {
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> "admin".equalsIgnoreCase(u.getRole()))
                .toList();
        for (User admin : admins) {
            createNotification(admin.getId(), title, message, type, referenceId);
        }
    }

    public void notifyUser(String username, String title, String message, String type, Long referenceId) {
        userRepository.findByUsername(username).ifPresent(u -> {
            createNotification(u.getId(), title, message, type, referenceId);
        });
    }

    public List<Notification> getNotificationsForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public boolean markAsRead(Long id, Long userId) {
        return notificationRepository.findById(id).map(n -> {
            if (n.getUserId().equals(userId)) {
                n.setRead(true);
                notificationRepository.save(n);
                return true;
            }
            return false;
        }).orElse(false);
    }

    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> !n.isRead())
                .toList();
        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);
    }
}

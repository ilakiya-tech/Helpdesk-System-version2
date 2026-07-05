package com.workflow.engine.service;

import com.workflow.engine.entity.LeaveRequest;
import com.workflow.engine.entity.User;
import com.workflow.engine.repository.LeaveRequestRepository;
import com.workflow.engine.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDate;
import java.util.List;

@Component
public class AvailabilityScheduler {

    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public AvailabilityScheduler(UserRepository userRepository, LeaveRequestRepository leaveRequestRepository) {
        this.userRepository = userRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    /**
     * Runs every hour to synchronize staff availability based on approved leave requests.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void syncAllStaffAvailability() {
        LocalDate today = LocalDate.now();
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if ("staff".equalsIgnoreCase(user.getRole())) {
                syncUserAvailability(user, today);
            }
        }
    }

    /**
     * Run the synchronization immediately when the Spring Boot server boots up.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        syncAllStaffAvailability();
    }

    public void syncUserAvailability(User user, LocalDate date) {
        List<LeaveRequest> leaves = leaveRequestRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        
        // Find if there is any approved leave request covering today or the future.
        boolean onLeaveNow = leaves.stream()
                .anyMatch(lr -> "APPROVED".equalsIgnoreCase(lr.getStatus()) &&
                        !date.isBefore(lr.getStartDate()) &&
                        !date.isAfter(lr.getEndDate()));

        String targetStatus = onLeaveNow ? "on_leave" : "available";
        if (!targetStatus.equalsIgnoreCase(user.getAvailability())) {
            user.setAvailability(targetStatus);
            userRepository.save(user);
        }
    }
}

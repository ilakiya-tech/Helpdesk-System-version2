package com.workflow.engine.repository;

import com.workflow.engine.entity.ActivityHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityHistoryRepository extends JpaRepository<ActivityHistory, Long> {
    List<ActivityHistory> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}

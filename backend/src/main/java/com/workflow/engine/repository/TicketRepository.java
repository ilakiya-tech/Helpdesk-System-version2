package com.workflow.engine.repository;

import com.workflow.engine.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStatusNot(String status);
    List<Ticket> findByStatusNotIn(java.util.Collection<String> statuses);
    List<Ticket> findByStatus(String status);
    long countByStatus(String status);
    long countBySlaStatus(String slaStatus);
    List<Ticket> findByStatusIn(java.util.Collection<String> statuses);
}

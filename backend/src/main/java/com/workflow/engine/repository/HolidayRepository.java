package com.workflow.engine.repository;

import com.workflow.engine.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    boolean existsByDate(LocalDate date);
}

package com.workflow.engine.config;

import com.workflow.engine.entity.Holiday;
import com.workflow.engine.repository.HolidayRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final HolidayRepository holidayRepository;

    public DatabaseSeeder(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    private static Holiday createHoliday(String name, LocalDate date, String type) {
        Holiday h = new Holiday();
        h.setName(name);
        h.setDate(date);
        h.setType(type);
        return h;
    }

    @Override
    public void run(String... args) {
        if (holidayRepository.count() == 0) {
            System.out.println("Seeding 2026 Indian Government Holidays...");
            List<Holiday> holidays = Arrays.asList(
                createHoliday("Republic Day", LocalDate.of(2026, 1, 26), "Public"),
                createHoliday("Maha Shivratri", LocalDate.of(2026, 2, 15), "Public"),
                createHoliday("Holi", LocalDate.of(2026, 3, 4), "Public"),
                createHoliday("Good Friday", LocalDate.of(2026, 4, 3), "Public"),
                createHoliday("Eid-ul-Fitr", LocalDate.of(2026, 3, 20), "Public"),
                createHoliday("Mahavir Jayanti", LocalDate.of(2026, 3, 31), "Public"),
                createHoliday("Buddha Purnima", LocalDate.of(2026, 5, 1), "Public"),
                createHoliday("Id-ul-Zuha (Bakrid)", LocalDate.of(2026, 5, 27), "Public"),
                createHoliday("Muharram", LocalDate.of(2026, 6, 25), "Public"),
                createHoliday("Independence Day", LocalDate.of(2026, 8, 15), "Public"),
                createHoliday("Janmashtami", LocalDate.of(2026, 9, 4), "Public"),
                createHoliday("Eid-e-Milad", LocalDate.of(2026, 9, 15), "Public"),
                createHoliday("Mahatma Gandhi Birthday", LocalDate.of(2026, 10, 2), "Public"),
                createHoliday("Dussehra", LocalDate.of(2026, 10, 20), "Public"),
                createHoliday("Diwali", LocalDate.of(2026, 11, 8), "Public"),
                createHoliday("Guru Nanak Birthday", LocalDate.of(2026, 11, 24), "Public"),
                createHoliday("Christmas Day", LocalDate.of(2026, 12, 25), "Public")
            );
            holidayRepository.saveAll(holidays);
            System.out.println("Holidays seeded successfully!");
        }
    }
}

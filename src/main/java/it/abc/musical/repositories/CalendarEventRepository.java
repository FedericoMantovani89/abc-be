package it.abc.musical.repositories;

import it.abc.musical.entities.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findByDeletedAtIsNullAndStartDatetimeBetweenOrderByStartDatetimeAsc(
            LocalDateTime from, LocalDateTime to);

    Optional<CalendarEvent> findByIdAndDeletedAtIsNull(Long id);
}

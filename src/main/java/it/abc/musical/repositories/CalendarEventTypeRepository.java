package it.abc.musical.repositories;

import it.abc.musical.entities.CalendarEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalendarEventTypeRepository extends JpaRepository<CalendarEventType, Long> {

    List<CalendarEventType> findByActiveTrueOrderByNameAsc();

    List<CalendarEventType> findAllByOrderByNameAsc();
}

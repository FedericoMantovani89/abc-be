package it.abc.musical.repositories;

import it.abc.musical.entities.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByDeletedAtIsNullOrderByEventDateAsc();

    List<Event> findByDeletedAtIsNullAndEventDateAfterOrderByEventDateAsc(LocalDateTime from);

    List<Event> findByDeletedAtIsNullAndEventDateBeforeOrderByEventDateDesc(LocalDateTime to);

    Optional<Event> findByIdAndDeletedAtIsNull(Long id);
}

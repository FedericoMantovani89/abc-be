package it.abc.musical.repositories;

import it.abc.musical.entities.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    /**
     * Eventi attivi che si sovrappongono all'intervallo [from, to): iniziano prima della fine e
     * finiscono dopo l'inizio. Un evento senza fine e' un istante e conta solo il suo inizio.
     */
    @Query("""
            select e from CalendarEvent e
            where e.deletedAt is null
              and e.startDatetime < :to
              and (e.endDatetime >= :from or (e.endDatetime is null and e.startDatetime >= :from))
            order by e.startDatetime asc
            """)
    List<CalendarEvent> findActiveOverlapping(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Optional<CalendarEvent> findByIdAndDeletedAtIsNull(Long id);

    /** Conta anche gli eventi soft-deleted: la FK event_type_id li lega ancora al tipo. */
    boolean existsByEventTypeId(Long eventTypeId);
}

package it.abc.musical.dto;

import it.abc.musical.entities.CalendarEvent;
import it.abc.musical.entities.CalendarEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CalendarDtos {

    private CalendarDtos() {
    }

    public record CalendarEventTypeDto(Long id, String name, String iconClass, String colorHex,
                                       boolean active, boolean isRehearsalType) {

        public static CalendarEventTypeDto from(CalendarEventType t) {
            return new CalendarEventTypeDto(t.getId(), t.getName(), t.getIconClass(),
                    t.getColorHex(), t.isActive(), t.isRehearsalType());
        }
    }

    public record CalendarEventTypeUpsertRequest(
            @NotBlank String name, String iconClass, String colorHex,
            Boolean active, Boolean isRehearsalType) {
    }

    /** Esito di DELETE su un tipo: DELETED (riga rimossa, type null) o DEACTIVATED (tipo in uso). */
    public record CalendarEventTypeDeleteResult(Long id, String outcome, String message,
                                                CalendarEventTypeDto type) {
    }

    public record CalendarEventDto(
            Long id, String title, String description,
            CalendarEventTypeDto eventType,
            LocalDateTime startDatetime, LocalDateTime endDatetime,
            String location, String venue,
            String targetRoles,
            Long showId, String showTitle,
            Set<String> rehearsalRoles) {

        public static CalendarEventDto from(CalendarEvent e) {
            return new CalendarEventDto(
                    e.getId(), e.getTitle(), e.getDescription(),
                    e.getEventType() != null ? CalendarEventTypeDto.from(e.getEventType()) : null,
                    e.getStartDatetime(), e.getEndDatetime(),
                    e.getLocation(), e.getVenue(),
                    e.getTargetRoles(),
                    e.getShow() != null ? e.getShow().getId() : null,
                    e.getShow() != null ? e.getShow().getTitle() : null,
                    copy(e.getRehearsalRoles()));
        }
    }

    public record CalendarEventUpsertRequest(
            @NotBlank String title,
            String description,
            Long eventTypeId,
            @NotNull LocalDateTime startDatetime,
            LocalDateTime endDatetime,
            String location,
            String venue,
            String targetRoles,
            Long showId,
            Set<String> rehearsalRoles) {
    }

    /**
     * Copia la collection lazy di Hibernate dentro la transazione: con open-in-view: false Jackson
     * serializza il DTO a sessione chiusa, e una PersistentSet non inizializzata esploderebbe li'.
     */
    private static Set<String> copy(Set<String> lazy) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(lazy));
    }
}

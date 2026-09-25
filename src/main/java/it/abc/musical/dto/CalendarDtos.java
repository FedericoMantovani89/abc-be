package it.abc.musical.dto;

import it.abc.musical.entities.CalendarEvent;
import it.abc.musical.entities.CalendarEventType;
import it.abc.musical.entities.ShowScene;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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

    public record SceneSummaryDto(Long id, String sceneNumber, String title, Set<String> castRoles) {

        public static SceneSummaryDto from(ShowScene s) {
            return new SceneSummaryDto(s.getId(), s.getSceneNumber(), s.getTitle(), copy(s.getCastRoles()));
        }
    }

    public record CalendarEventDto(
            Long id, String title, String description,
            CalendarEventTypeDto eventType,
            LocalDateTime startDatetime, LocalDateTime endDatetime,
            String location, String venue,
            boolean isRecurring, String recurrencePattern,
            Long publicEventId, String targetRoles,
            Long showId, String showTitle,
            Set<String> rehearsalRoles, List<SceneSummaryDto> scenes) {

        public static CalendarEventDto from(CalendarEvent e) {
            return new CalendarEventDto(
                    e.getId(), e.getTitle(), e.getDescription(),
                    e.getEventType() != null ? CalendarEventTypeDto.from(e.getEventType()) : null,
                    e.getStartDatetime(), e.getEndDatetime(),
                    e.getLocation(), e.getVenue(),
                    e.isRecurring(), e.getRecurrencePattern(),
                    e.getPublicEventId(), e.getTargetRoles(),
                    e.getShow() != null ? e.getShow().getId() : null,
                    e.getShow() != null ? e.getShow().getTitle() : null,
                    copy(e.getRehearsalRoles()),
                    e.getScenes().stream()
                            .sorted(Comparator.comparing(ShowScene::getSortOrder)
                                    .thenComparing(ShowScene::getId))
                            .map(SceneSummaryDto::from)
                            .toList());
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
            Boolean isRecurring,
            String recurrencePattern,
            Long publicEventId,
            String targetRoles,
            Long showId,
            List<Long> sceneIds,
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

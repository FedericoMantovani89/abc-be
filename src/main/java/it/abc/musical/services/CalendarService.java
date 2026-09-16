package it.abc.musical.services;

import it.abc.musical.dto.CalendarDtos.CalendarEventDto;
import it.abc.musical.dto.CalendarDtos.CalendarEventTypeDto;
import it.abc.musical.dto.CalendarDtos.CalendarEventTypeUpsertRequest;
import it.abc.musical.dto.CalendarDtos.CalendarEventUpsertRequest;
import it.abc.musical.entities.CalendarEvent;
import it.abc.musical.entities.CalendarEventType;
import it.abc.musical.entities.ShowScene;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.CalendarEventRepository;
import it.abc.musical.repositories.CalendarEventTypeRepository;
import it.abc.musical.repositories.ShowRepository;
import it.abc.musical.repositories.ShowSceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventTypeRepository calendarEventTypeRepository;
    private final ShowRepository showRepository;
    private final ShowSceneRepository showSceneRepository;
    private final AuditLogService auditLogService;

    // ------------------------------------------------------------------ letture

    /** Eventi del mese. Se userRoles è valorizzato filtra per target_roles (vista soci). */
    @Transactional(readOnly = true)
    public List<CalendarEventDto> monthEvents(int year, int month, Set<String> userRoles) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().atTime(23, 59, 59);
        return calendarEventRepository
                .findByDeletedAtIsNullAndStartDatetimeBetweenOrderByStartDatetimeAsc(from, to).stream()
                .filter(e -> userRoles == null
                        || it.abc.musical.util.AuthUtil.matchesTargetRoles(e.getTargetRoles(), userRoles))
                .map(CalendarEventDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CalendarEventDto getDetail(Long id) {
        return CalendarEventDto.from(activeEvent(id));
    }

    // ------------------------------------------------------------------ admin CRUD

    @Transactional
    public CalendarEventDto create(CalendarEventUpsertRequest request, Long userId) {
        CalendarEvent event = new CalendarEvent();
        event.setCreatedBy(userId);
        applyRequest(event, request, userId);
        event = calendarEventRepository.save(event);
        auditLogService.record("CREATE", "CalendarEvent", event.getId());
        return CalendarEventDto.from(event);
    }

    @Transactional
    public CalendarEventDto update(Long id, CalendarEventUpsertRequest request, Long userId) {
        CalendarEvent event = activeEvent(id);
        applyRequest(event, request, userId);
        event = calendarEventRepository.save(event);
        auditLogService.record("UPDATE", "CalendarEvent", event.getId());
        return CalendarEventDto.from(event);
    }

    @Transactional
    public void softDelete(Long id) {
        CalendarEvent event = activeEvent(id);
        event.setDeletedAt(LocalDateTime.now());
        calendarEventRepository.save(event);
        auditLogService.record("DELETE", "CalendarEvent", id);
    }

    // ------------------------------------------------------------------ tipi

    @Transactional(readOnly = true)
    public List<CalendarEventTypeDto> activeTypes() {
        return calendarEventTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(CalendarEventTypeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CalendarEventTypeDto> allTypes() {
        return calendarEventTypeRepository.findAllByOrderByNameAsc().stream()
                .map(CalendarEventTypeDto::from).toList();
    }

    @Transactional
    public CalendarEventTypeDto createType(CalendarEventTypeUpsertRequest request) {
        CalendarEventType type = new CalendarEventType();
        applyTypeRequest(type, request);
        return CalendarEventTypeDto.from(calendarEventTypeRepository.save(type));
    }

    @Transactional
    public CalendarEventTypeDto updateType(Long id, CalendarEventTypeUpsertRequest request) {
        CalendarEventType type = calendarEventTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tipo evento non trovato"));
        applyTypeRequest(type, request);
        return CalendarEventTypeDto.from(calendarEventTypeRepository.save(type));
    }

    // ------------------------------------------------------------------ internals

    private CalendarEvent activeEvent(Long id) {
        return calendarEventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Evento calendario non trovato"));
    }

    private void applyRequest(CalendarEvent event, CalendarEventUpsertRequest request, Long userId) {
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setEventType(request.eventTypeId() != null
                ? calendarEventTypeRepository.findById(request.eventTypeId())
                        .orElseThrow(() -> new NotFoundException("Tipo evento non trovato"))
                : null);
        event.setStartDatetime(request.startDatetime());
        event.setEndDatetime(request.endDatetime());
        event.setLocation(request.location());
        event.setVenue(request.venue());
        event.setRecurring(Boolean.TRUE.equals(request.isRecurring()));
        event.setRecurrencePattern(request.recurrencePattern());
        event.setPublicEventId(request.publicEventId());
        event.setTargetRoles(request.targetRoles());
        event.setShow(request.showId() != null
                ? showRepository.findByIdAndDeletedAtIsNull(request.showId())
                        .orElseThrow(() -> new NotFoundException("Spettacolo non trovato"))
                : null);
        event.setUpdatedBy(userId);

        // rehearsalRoles è la fonte di verità per i convocati; le scene sono il dato di pianificazione.
        event.setRehearsalRoles(request.rehearsalRoles() != null
                ? new LinkedHashSet<>(request.rehearsalRoles()) : new LinkedHashSet<>());

        Set<ShowScene> scenes = new LinkedHashSet<>();
        if (request.sceneIds() != null && !request.sceneIds().isEmpty()) {
            if (request.showId() == null) {
                throw new BadRequestException("Le scene richiedono uno spettacolo");
            }
            for (Long sceneId : request.sceneIds()) {
                scenes.add(showSceneRepository.findByIdAndShowId(sceneId, request.showId())
                        .orElseThrow(() -> new NotFoundException(
                                "Scena " + sceneId + " non trovata per lo spettacolo")));
            }
        }
        event.setScenes(scenes);
    }

    private void applyTypeRequest(CalendarEventType type, CalendarEventTypeUpsertRequest request) {
        type.setName(request.name().trim());
        type.setIconClass(request.iconClass());
        type.setColorHex(request.colorHex());
        if (request.active() != null) {
            type.setActive(request.active());
        }
        if (request.isRehearsalType() != null) {
            type.setRehearsalType(request.isRehearsalType());
        }
    }
}

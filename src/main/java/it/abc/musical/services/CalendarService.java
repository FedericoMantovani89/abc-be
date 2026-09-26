package it.abc.musical.services;

import it.abc.musical.dto.CalendarDtos.CalendarEventDto;
import it.abc.musical.dto.CalendarDtos.CalendarEventTypeDeleteResult;
import it.abc.musical.dto.CalendarDtos.CalendarEventTypeDto;
import it.abc.musical.dto.CalendarDtos.CalendarEventTypeUpsertRequest;
import it.abc.musical.dto.CalendarDtos.CalendarEventUpsertRequest;
import it.abc.musical.entities.CalendarEvent;
import it.abc.musical.entities.CalendarEventType;
import it.abc.musical.entities.Show;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.CalendarEventRepository;
import it.abc.musical.repositories.CalendarEventTypeRepository;
import it.abc.musical.repositories.RoleRepository;
import it.abc.musical.repositories.ShowRepository;
import it.abc.musical.util.AuthUtil;
import it.abc.musical.util.RoleCsv;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventTypeRepository calendarEventTypeRepository;
    private final ShowRepository showRepository;
    private final ShowService showService;
    private final AuditLogService auditLogService;
    private final RoleRepository roleRepository;

    // ------------------------------------------------------------------ letture

    /**
     * Eventi che si sovrappongono al mese, anche se iniziano prima o finiscono dopo.
     * Se userRoles è valorizzato filtra per target_roles (vista soci).
     */
    @Transactional(readOnly = true)
    public List<CalendarEventDto> monthEvents(int year, int month, Set<String> userRoles) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();
        return calendarEventRepository.findActiveOverlapping(from, to).stream()
                .filter(e -> userRoles == null
                        || AuthUtil.matchesTargetRoles(e.getTargetRoles(), userRoles))
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
        type = calendarEventTypeRepository.save(type);
        auditLogService.record("CREATE", "CalendarEventType", type.getId());
        return CalendarEventTypeDto.from(type);
    }

    @Transactional
    public CalendarEventTypeDto updateType(Long id, CalendarEventTypeUpsertRequest request) {
        CalendarEventType type = requireType(id);
        applyTypeRequest(type, request);
        type = calendarEventTypeRepository.save(type);
        auditLogService.record("UPDATE", "CalendarEventType", type.getId());
        return CalendarEventTypeDto.from(type);
    }

    /**
     * Cancella il tipo solo se nessun evento (nemmeno soft-deleted) lo referenzia; altrimenti lo
     * disattiva, cosi' sparisce dai tipi selezionabili ma gli eventi esistenti lo mostrano ancora.
     */
    @Transactional
    public CalendarEventTypeDeleteResult deleteType(Long id) {
        CalendarEventType type = requireType(id);
        if (calendarEventRepository.existsByEventTypeId(id)) {
            type.setActive(false);
            type = calendarEventTypeRepository.save(type);
            auditLogService.record("DEACTIVATE", "CalendarEventType", id);
            return new CalendarEventTypeDeleteResult(id, "DEACTIVATED",
                    "Tipo in uso da almeno un evento: e' stato disattivato",
                    CalendarEventTypeDto.from(type));
        }
        calendarEventTypeRepository.delete(type);
        auditLogService.record("DELETE", "CalendarEventType", id);
        return new CalendarEventTypeDeleteResult(id, "DELETED", "Tipo eliminato", null);
    }

    // ------------------------------------------------------------------ internals

    private CalendarEvent activeEvent(Long id) {
        return calendarEventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Evento calendario non trovato"));
    }

    private CalendarEventType requireType(Long id) {
        return calendarEventTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tipo evento non trovato"));
    }

    private void applyRequest(CalendarEvent event, CalendarEventUpsertRequest request, Long userId) {
        if (request.endDatetime() != null && request.endDatetime().isBefore(request.startDatetime())) {
            throw new BadRequestException("La data di fine precede la data di inizio");
        }
        CalendarEventType type = request.eventTypeId() != null ? requireType(request.eventTypeId()) : null;
        // Validazione prima di toccare l'entita': un 400 non lascia modifiche a meta'.
        Show show = null;
        Set<String> rehearsalRoles = new LinkedHashSet<>();
        if (type != null && type.isRehearsalType()) {
            show = request.showId() != null
                    ? showRepository.findByIdAndDeletedAtIsNull(request.showId())
                            .orElseThrow(() -> new NotFoundException("Spettacolo non trovato"))
                    : null;
            rehearsalRoles = validRehearsalRoles(show, request.rehearsalRoles());
        }

        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setEventType(type);
        event.setStartDatetime(request.startDatetime());
        event.setEndDatetime(request.endDatetime());
        event.setLocation(request.location());
        event.setVenue(request.venue());
        List<String> targetRoles = RoleCsv.parse(request.targetRoles());
        RoleCsv.requireKnownRoles(targetRoles, role -> roleRepository.findByName(role).isPresent());
        event.setTargetRoles(RoleCsv.format(targetRoles));
        // Spettacolo e ruoli convocati valgono solo per i tipi prova: sugli altri restano vuoti.
        event.setShow(show);
        event.setRehearsalRoles(rehearsalRoles);
        event.setUpdatedBy(userId);
    }

    /**
     * I ruoli convocati devono esistere nel cast dello spettacolo (stessa fonte di
     * GET /api/admin/shows/{id}/cast-roles). Il confronto ignora maiuscole/minuscole e salva la
     * grafia del cast.
     */
    private Set<String> validRehearsalRoles(Show show, Set<String> requested) {
        Set<String> roles = new LinkedHashSet<>();
        if (requested == null || requested.isEmpty()) {
            return roles;
        }
        if (show == null) {
            throw new BadRequestException("I ruoli della prova richiedono uno spettacolo");
        }
        Map<String, String> castByLowerCase = showService.castRoles(show.getId()).stream()
                .collect(Collectors.toMap(r -> r.toLowerCase(Locale.ITALIAN), Function.identity(), (a, b) -> a));
        for (String role : requested) {
            String canonical = role != null ? castByLowerCase.get(role.trim().toLowerCase(Locale.ITALIAN)) : null;
            if (canonical == null) {
                throw new BadRequestException("Il ruolo \"" + role + "\" non esiste nel cast dello spettacolo");
            }
            roles.add(canonical);
        }
        return roles;
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

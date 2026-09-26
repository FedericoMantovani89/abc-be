package it.abc.musical.services;

import it.abc.musical.dto.AdminEventDtos.EventUpsertRequest;
import it.abc.musical.dto.EventDtos.EventDetailDto;
import it.abc.musical.dto.EventDtos.EventSummaryDto;
import it.abc.musical.entities.Event;
import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.EventRepository;
import it.abc.musical.repositories.EventTypeRepository;
import it.abc.musical.repositories.ShowRepository;
import it.abc.musical.util.HeroCropRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final ShowRepository showRepository;
    private final StorageService storageService;
    private final FileValidationService fileValidationService;
    private final AuditLogService auditLogService;

    // ------------------------------------------------------------------ public

    @Transactional(readOnly = true)
    public List<EventSummaryDto> listPublic() {
        return eventRepository.findByDeletedAtIsNullOrderByEventDateAsc().stream()
                .map(EventSummaryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventDetailDto getPublicDetail(Long id) {
        return EventDetailDto.from(activeEvent(id));
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public List<EventSummaryDto> listAdmin(String status) {
        LocalDateTime now = LocalDateTime.now();
        List<Event> events = switch (status == null ? "" : status) {
            case "future" -> eventRepository.findByDeletedAtIsNullAndEventDateAfterOrderByEventDateAsc(now);
            case "past" -> eventRepository.findByDeletedAtIsNullAndEventDateBeforeOrderByEventDateDesc(now);
            default -> eventRepository.findByDeletedAtIsNullOrderByEventDateAsc();
        };
        return events.stream().map(EventSummaryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public EventDetailDto getAdminDetail(Long id) {
        return EventDetailDto.from(activeEvent(id));
    }

    @Transactional(readOnly = true)
    public List<EventTypeDto> types() {
        return eventTypeRepository.findAll().stream()
                .map(t -> new EventTypeDto(t.getId(), t.getName(), t.getDescription()))
                .toList();
    }

    public record EventTypeDto(Long id, String name, String description) {
    }

    @Transactional
    public Event create(EventUpsertRequest request, MultipartFile poster, Long userId) {
        Event event = new Event();
        event.setCreatedBy(userId);
        applyRequest(event, request, userId);
        if (poster != null && !poster.isEmpty()) {
            event.setPosterImageUrl(storePoster(poster));
        } else if (request.posterSourceEventId() != null) {
            event.setPosterImageUrl(clonePoster(request.posterSourceEventId()));
            if (event.getPosterImageUrl() != null
                    && event.getHeroFocusX() == null && event.getHeroFocusY() == null) {
                copyHeroFocusFromSource(event, request.posterSourceEventId());
            }
            if (event.getPosterImageUrl() != null
                    && event.getHeroFocusMobileX() == null && event.getHeroFocusMobileY() == null) {
                copyHeroFocusMobileFromSource(event, request.posterSourceEventId());
            }
            if (event.getPosterImageUrl() != null
                    && event.getHeroZoomDesktop() == null && event.getHeroZoomMobile() == null) {
                copyHeroZoomFromSource(event, request.posterSourceEventId());
            }
        }
        event = eventRepository.save(event);
        auditLogService.record("CREATE", "Event", event.getId());
        return event;
    }

    @Transactional
    public Event update(Long id, EventUpsertRequest request, MultipartFile poster, Long userId) {
        Event event = activeEvent(id);
        applyRequest(event, request, userId);
        if (poster != null && !poster.isEmpty()) {
            String oldPoster = event.getPosterImageUrl();
            event.setPosterImageUrl(storePoster(poster));
            storageService.deleteAfterCommit(oldPoster);
        }
        event = eventRepository.save(event);
        auditLogService.record("UPDATE", "Event", event.getId());
        return event;
    }

    @Transactional
    public void softDelete(Long id) {
        Event event = activeEvent(id);
        event.setDeletedAt(LocalDateTime.now());
        eventRepository.save(event);
        auditLogService.record("DELETE", "Event", id);
    }

    // ------------------------------------------------------------------ internals

    private Event activeEvent(Long id) {
        return eventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Evento non trovato"));
    }

    private void applyRequest(Event event, EventUpsertRequest request, Long userId) {
        validateBookingWindow(request.bookingOpenAt(), request.bookingCloseAt(), request.eventDate());
        HeroCropRules.validateFocus(request.heroFocusX(), request.heroFocusY());
        HeroCropRules.validateFocus(request.heroFocusMobileX(), request.heroFocusMobileY());
        HeroCropRules.validateZoom(request.heroZoomDesktop(), request.heroZoomMobile());
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setEventDate(request.eventDate());
        event.setLocationVenue(request.locationVenue().trim());
        event.setLocationAddress(request.locationAddress());
        event.setLocationCity(request.locationCity());
        event.setLocationProvince(request.locationProvince());
        event.setBookingOpenAt(request.bookingOpenAt());
        event.setBookingCloseAt(request.bookingCloseAt());
        event.setBookingLink(request.bookingLink());
        event.setContactEmail(request.contactEmail());
        event.setContactPhone(request.contactPhone());
        event.setEventType(request.eventTypeId() != null
                ? eventTypeRepository.findById(request.eventTypeId())
                        .orElseThrow(() -> new NotFoundException("Tipo evento non trovato"))
                : null);
        event.setShow(request.showId() != null
                ? showRepository.findByIdAndDeletedAtIsNull(request.showId())
                        .orElseThrow(() -> new NotFoundException("Spettacolo non trovato"))
                : null);
        event.setHeroFocusX(request.heroFocusX());
        event.setHeroFocusY(request.heroFocusY());
        event.setHeroFocusMobileX(request.heroFocusMobileX());
        event.setHeroFocusMobileY(request.heroFocusMobileY());
        event.setHeroZoomDesktop(request.heroZoomDesktop());
        event.setHeroZoomMobile(request.heroZoomMobile());
        event.setUpdatedBy(userId);
    }

    /**
     * Le date di prenotazione restano facoltative: un evento senza nessuna delle due, o con
     * una sola, è legittimo e non viene toccato qui. Controllato solo in scrittura (create/
     * update) così una riga già in database con una finestra impossibile resta leggibile
     * finché non viene corretta da qui.
     */
    private void validateBookingWindow(LocalDateTime bookingOpenAt, LocalDateTime bookingCloseAt,
            LocalDateTime eventDate) {
        if (bookingOpenAt != null && bookingCloseAt != null && !bookingOpenAt.isBefore(bookingCloseAt)) {
            throw new BadRequestException(
                    "L'apertura delle prenotazioni deve essere precedente alla chiusura.");
        }
        if (bookingCloseAt != null && bookingCloseAt.isAfter(eventDate)) {
            throw new BadRequestException(
                    "La chiusura delle prenotazioni non può essere successiva alla data dell'evento.");
        }
    }

    private String storePoster(MultipartFile poster) {
        fileValidationService.validate(poster, UploadTargetType.SHOW_POSTER);
        return storageService.store(poster, UploadTargetType.SHOW_POSTER);
    }

    /**
     * Clona la locandina di un altro evento in un file nuovo e indipendente, senza far
     * transitare i byte dal client. Chiamata solo da create(), dopo che applyRequest() ha
     * già validato la finestra di prenotazione: se la richiesta viene rifiutata per quello,
     * questo metodo non viene mai raggiunto e nessun file finisce copiato sul disco.
     */
    private String clonePoster(Long sourceEventId) {
        Event source = eventRepository.findByIdAndDeletedAtIsNull(sourceEventId)
                .orElseThrow(() -> new BadRequestException("Evento di origine non trovato"));
        String sourcePosterUrl = source.getPosterImageUrl();
        if (sourcePosterUrl == null || sourcePosterUrl.isBlank()) {
            return null;
        }
        return storageService.copy(sourcePosterUrl, UploadTargetType.SHOW_POSTER);
    }

    /**
     * Il punto focale ha senso solo insieme all'immagine a cui si riferisce: quando la
     * locandina viene clonata da un altro evento e la richiesta non porta già un proprio punto
     * focale, viaggia insieme ad essa quello dell'evento di origine invece di restare vuoto.
     */
    private void copyHeroFocusFromSource(Event event, Long sourceEventId) {
        eventRepository.findByIdAndDeletedAtIsNull(sourceEventId).ifPresent(source -> {
            event.setHeroFocusX(source.getHeroFocusX());
            event.setHeroFocusY(source.getHeroFocusY());
        });
    }

    /**
     * Come copyHeroFocusFromSource, ma per il punto focale mobile: indipendente da quello
     * desktop, viaggia insieme alla locandina clonata con la stessa regola.
     */
    private void copyHeroFocusMobileFromSource(Event event, Long sourceEventId) {
        eventRepository.findByIdAndDeletedAtIsNull(sourceEventId).ifPresent(source -> {
            event.setHeroFocusMobileX(source.getHeroFocusMobileX());
            event.setHeroFocusMobileY(source.getHeroFocusMobileY());
        });
    }

    /**
     * Come copyHeroFocusFromSource, ma per i due fattori di zoom: hanno senso solo insieme
     * all'immagine a cui si riferiscono, quindi viaggiano con essa quando la locandina viene
     * clonata e la richiesta non ne porta già uno proprio.
     */
    private void copyHeroZoomFromSource(Event event, Long sourceEventId) {
        eventRepository.findByIdAndDeletedAtIsNull(sourceEventId).ifPresent(source -> {
            event.setHeroZoomDesktop(source.getHeroZoomDesktop());
            event.setHeroZoomMobile(source.getHeroZoomMobile());
        });
    }
}

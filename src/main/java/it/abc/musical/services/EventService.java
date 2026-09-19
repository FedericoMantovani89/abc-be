package it.abc.musical.services;

import it.abc.musical.dto.AdminEventDtos.EventUpsertRequest;
import it.abc.musical.dto.EventDtos.EventDetailDto;
import it.abc.musical.dto.EventDtos.EventSummaryDto;
import it.abc.musical.entities.Event;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.EventRepository;
import it.abc.musical.repositories.EventTypeRepository;
import it.abc.musical.repositories.ShowRepository;
import it.abc.musical.util.FileTypeUtil;
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
        }
        return eventRepository.save(event);
    }

    @Transactional
    public Event update(Long id, EventUpsertRequest request, MultipartFile poster, Long userId) {
        Event event = activeEvent(id);
        applyRequest(event, request, userId);
        if (poster != null && !poster.isEmpty()) {
            storageService.delete(event.getPosterImageUrl());
            event.setPosterImageUrl(storePoster(poster));
        }
        return eventRepository.save(event);
    }

    @Transactional
    public void softDelete(Long id) {
        Event event = activeEvent(id);
        event.setDeletedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    // ------------------------------------------------------------------ internals

    private Event activeEvent(Long id) {
        return eventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Evento non trovato"));
    }

    private void applyRequest(Event event, EventUpsertRequest request, Long userId) {
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setEventDate(request.eventDate());
        event.setLocationVenue(request.locationVenue().trim());
        event.setLocationAddress(request.locationAddress());
        event.setLocationCity(request.locationCity());
        event.setLocationProvince(request.locationProvince());
        validateBookingWindow(request.bookingOpenAt(), request.bookingCloseAt(), request.eventDate());
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
        String extension = StorageService.extensionOf(poster.getOriginalFilename());
        if (FileTypeUtil.categoryOf(extension) != FileTypeUtil.Category.IMAGE) {
            throw new BadRequestException("Sono ammesse solo immagini JPG o PNG");
        }
        fileValidationService.validate(poster);
        return storageService.store(poster, StorageService.POSTERS_DIR);
    }
}

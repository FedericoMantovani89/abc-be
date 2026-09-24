package it.abc.musical.services;

import it.abc.musical.dto.AdminEventDtos.EventUpsertRequest;
import it.abc.musical.dto.EventDtos.EventDetailDto;
import it.abc.musical.entities.Event;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.repositories.EventRepository;
import it.abc.musical.repositories.EventTypeRepository;
import it.abc.musical.repositories.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regole sulla finestra di prenotazione di un evento (apertura/chiusura), imposte solo in
 * scrittura: create/update rifiutano una finestra impossibile, ma una riga già in database
 * con una finestra impossibile (come l'evento id=1 reale) resta leggibile finché non viene
 * corretta da qui.
 */
class EventServiceTest {

    private EventRepository eventRepository;
    private EventService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        EventTypeRepository eventTypeRepository = mock(EventTypeRepository.class);
        ShowRepository showRepository = mock(ShowRepository.class);
        StorageService storageService = mock(StorageService.class);
        FileValidationService fileValidationService = mock(FileValidationService.class);
        service = new EventService(eventRepository, eventTypeRepository, showRepository,
                storageService, fileValidationService);
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static EventUpsertRequest request(LocalDateTime open, LocalDateTime close, LocalDateTime eventDate) {
        return new EventUpsertRequest(
                "Saggio di fine anno", null, eventDate, "Teatro Comunale",
                null, null, null,
                open, close, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void rejectsOpeningAfterClosing() {
        LocalDateTime eventDate = LocalDateTime.of(2027, 1, 9, 20, 0);
        EventUpsertRequest bad = request(
                LocalDateTime.of(2027, 2, 1, 0, 0),
                LocalDateTime.of(2027, 1, 8, 0, 0),
                eventDate);

        assertThatThrownBy(() -> service.create(bad, null, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'apertura delle prenotazioni deve essere precedente alla chiusura.");
    }

    @Test
    void rejectsClosingAfterEventDate() {
        LocalDateTime eventDate = LocalDateTime.of(2027, 1, 9, 20, 0);
        EventUpsertRequest bad = request(
                LocalDateTime.of(2026, 12, 1, 0, 0),
                LocalDateTime.of(2027, 1, 10, 0, 0),
                eventDate);

        assertThatThrownBy(() -> service.create(bad, null, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La chiusura delle prenotazioni non può essere successiva alla data dell'evento.");
    }

    @Test
    void acceptsAValidBookingWindow() {
        LocalDateTime eventDate = LocalDateTime.of(2027, 1, 9, 20, 0);
        EventUpsertRequest ok = request(
                LocalDateTime.of(2026, 12, 1, 0, 0),
                LocalDateTime.of(2027, 1, 8, 0, 0),
                eventDate);

        Event saved = service.create(ok, null, 1L);

        assertThat(saved.getBookingOpenAt()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(saved.getBookingCloseAt()).isEqualTo(LocalDateTime.of(2027, 1, 8, 0, 0));
    }

    @Test
    void acceptsAnEventWithNoBookingWindowAtAll() {
        EventUpsertRequest ok = request(null, null, LocalDateTime.of(2027, 1, 9, 20, 0));

        Event saved = service.create(ok, null, 1L);

        assertThat(saved.getBookingOpenAt()).isNull();
        assertThat(saved.getBookingCloseAt()).isNull();
    }

    @Test
    void acceptsAnEventWithOnlyTheClosingDate() {
        EventUpsertRequest ok = request(null, LocalDateTime.of(2027, 1, 8, 0, 0), LocalDateTime.of(2027, 1, 9, 20, 0));

        Event saved = service.create(ok, null, 1L);

        assertThat(saved.getBookingOpenAt()).isNull();
        assertThat(saved.getBookingCloseAt()).isEqualTo(LocalDateTime.of(2027, 1, 8, 0, 0));
    }

    private static EventUpsertRequest requestWithHeroFocus(Integer heroFocusX, Integer heroFocusY) {
        return new EventUpsertRequest(
                "Saggio di fine anno", null, LocalDateTime.of(2027, 1, 9, 20, 0), "Teatro Comunale",
                null, null, null, null, null, null, null, null, null, null, null,
                heroFocusX, heroFocusY, null, null);
    }

    @Test
    void rejectsHeroFocusWithOnlyOneCoordinate() {
        assertThatThrownBy(() -> service.create(requestWithHeroFocus(30, null), null, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Punto focale non valido");
    }

    @Test
    void rejectsHeroFocusOutOfRange() {
        assertThatThrownBy(() -> service.create(requestWithHeroFocus(30, 101), null, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Punto focale non valido");
    }

    @Test
    void acceptsHeroFocusWhenBothCoordinatesAreNull() {
        Event saved = service.create(requestWithHeroFocus(null, null), null, 1L);

        assertThat(saved.getHeroFocusX()).isNull();
        assertThat(saved.getHeroFocusY()).isNull();
    }

    @Test
    void acceptsHeroFocusWhenBothCoordinatesAreInRange() {
        Event saved = service.create(requestWithHeroFocus(20, 80), null, 1L);

        assertThat(saved.getHeroFocusX()).isEqualTo(20);
        assertThat(saved.getHeroFocusY()).isEqualTo(80);
    }

    private static EventUpsertRequest requestWithHeroZoom(Integer heroZoomDesktop, Integer heroZoomMobile) {
        return new EventUpsertRequest(
                "Saggio di fine anno", null, LocalDateTime.of(2027, 1, 9, 20, 0), "Teatro Comunale",
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, heroZoomDesktop, heroZoomMobile);
    }

    @Test
    void rejectsHeroZoomDesktopOutOfRange() {
        assertThatThrownBy(() -> service.create(requestWithHeroZoom(9, null), null, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Zoom non valido");
    }

    @Test
    void rejectsHeroZoomMobileOutOfRange() {
        assertThatThrownBy(() -> service.create(requestWithHeroZoom(null, 301), null, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Zoom non valido");
    }

    @Test
    void acceptsHeroZoomWhenBothAreNull() {
        Event saved = service.create(requestWithHeroZoom(null, null), null, 1L);

        assertThat(saved.getHeroZoomDesktop()).isNull();
        assertThat(saved.getHeroZoomMobile()).isNull();
    }

    @Test
    void acceptsHeroZoomWhenIndependentlyInRange() {
        Event saved = service.create(requestWithHeroZoom(10, 300), null, 1L);

        assertThat(saved.getHeroZoomDesktop()).isEqualTo(10);
        assertThat(saved.getHeroZoomMobile()).isEqualTo(300);
    }

    /**
     * Simula l'evento id=1 reale (apertura 2027-02-01, chiusura 2027-01-08, evento
     * 2027-01-09): una finestra impossibile già scritta prima che questa validazione
     * esistesse.
     */
    @Test
    void anExistingEventWithAnImpossibleWindowCanStillBeReadAndThenFixed() {
        LocalDateTime eventDate = LocalDateTime.of(2027, 1, 9, 0, 0);
        Event existing = new Event();
        existing.setId(1L);
        existing.setTitle("Saggio");
        existing.setLocationVenue("Teatro Comunale");
        existing.setEventDate(eventDate);
        existing.setBookingOpenAt(LocalDateTime.of(2027, 2, 1, 0, 0));
        existing.setBookingCloseAt(LocalDateTime.of(2027, 1, 8, 0, 0));
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(existing));

        // LETTURA: la riga già sbagliata si legge senza eccezioni, valori invariati.
        EventDetailDto detail = service.getAdminDetail(1L);
        assertThat(detail.bookingOpenAt()).isEqualTo(LocalDateTime.of(2027, 2, 1, 0, 0));
        assertThat(detail.bookingCloseAt()).isEqualTo(LocalDateTime.of(2027, 1, 8, 0, 0));

        // SCRITTURA con la stessa finestra impossibile: rifiutata.
        EventUpsertRequest stillBad = request(
                LocalDateTime.of(2027, 2, 1, 0, 0), LocalDateTime.of(2027, 1, 8, 0, 0), eventDate);
        assertThatThrownBy(() -> service.update(1L, stillBad, null, 1L))
                .isInstanceOf(BadRequestException.class);

        // SCRITTURA corretta: va a buon fine, come deve poter fare Federico dall'area admin.
        EventUpsertRequest fixed = request(
                LocalDateTime.of(2026, 12, 1, 0, 0), LocalDateTime.of(2027, 1, 8, 0, 0), eventDate);
        Event updated = service.update(1L, fixed, null, 1L);
        assertThat(updated.getBookingOpenAt()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(updated.getBookingCloseAt()).isEqualTo(LocalDateTime.of(2027, 1, 8, 0, 0));
    }
}

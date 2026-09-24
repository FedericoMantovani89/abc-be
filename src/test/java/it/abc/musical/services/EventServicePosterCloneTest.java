package it.abc.musical.services;

import it.abc.musical.dto.AdminEventDtos.EventUpsertRequest;
import it.abc.musical.entities.Event;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.repositories.EventRepository;
import it.abc.musical.repositories.EventTypeRepository;
import it.abc.musical.repositories.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La console admin "clona" un evento scaricando la locandina dell'originale dal browser e
 * ricaricandola come file nuovo sulla richiesta di creazione (vedi AdminEventsManager.tsx,
 * openClone): il backend non sa che si tratta di un clone, vede solo due create() con lo
 * stesso contenuto immagine. Questo test dimostra che StorageService, usato da
 * EventService.create() via storePoster(), produce per i due upload un file indipendente:
 * path diverso, stesso contenuto, e la cancellazione dell'uno non tocca l'altro.
 */
class EventServicePosterCloneTest {

    @TempDir
    Path uploadDir;

    private EventService service;
    private StorageService storageService;
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() throws IOException {
        eventRepository = mock(EventRepository.class);
        EventTypeRepository eventTypeRepository = mock(EventTypeRepository.class);
        ShowRepository showRepository = mock(ShowRepository.class);
        FileValidationService fileValidationService = mock(FileValidationService.class);

        storageService = new StorageService(uploadDir.toString());
        storageService.init();

        service = new EventService(eventRepository, eventTypeRepository, showRepository,
                storageService, fileValidationService);
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static EventUpsertRequest request() {
        return requestWithPosterSource(null);
    }

    private static EventUpsertRequest requestWithBookingWindow(LocalDateTime bookingOpenAt, LocalDateTime bookingCloseAt) {
        return new EventUpsertRequest(
                "Saggio di fine anno", null, LocalDateTime.of(2027, 1, 9, 20, 0), "Teatro Comunale",
                null, null, null, bookingOpenAt, bookingCloseAt, null, null, null, null, null, null, null, null);
    }

    private static EventUpsertRequest requestWithPosterSource(Long posterSourceEventId) {
        return new EventUpsertRequest(
                "Saggio di fine anno", null, LocalDateTime.of(2027, 1, 9, 20, 0), "Teatro Comunale",
                null, null, null, null, null, null, null, null, null, null, posterSourceEventId, null, null);
    }

    @Test
    void cloningAnEventProducesAnIndependentPosterFile() throws IOException {
        byte[] bytes = "contenuto-finto-della-locandina".getBytes();
        MockMultipartFile originalUpload = new MockMultipartFile("poster", "locandina.jpg", "image/jpeg", bytes);
        MockMultipartFile clonedUpload = new MockMultipartFile("poster", "locandina.jpg", "image/jpeg", bytes);

        Event original = service.create(request(), originalUpload, 1L);
        Event clone = service.create(request(), clonedUpload, 1L);

        // Percorsi diversi, entrambi presenti su disco, stesso contenuto.
        assertThat(clone.getPosterImageUrl()).isNotEqualTo(original.getPosterImageUrl());
        Path originalPath = storageService.resolve(original.getPosterImageUrl());
        Path clonePath = storageService.resolve(clone.getPosterImageUrl());
        assertThat(Files.exists(originalPath)).isTrue();
        assertThat(Files.exists(clonePath)).isTrue();
        assertThat(Files.readAllBytes(clonePath)).isEqualTo(Files.readAllBytes(originalPath));

        // Indipendenza: cancellare la locandina dell'originale non tocca quella del clone.
        storageService.delete(original.getPosterImageUrl());
        assertThat(Files.exists(originalPath)).isFalse();
        assertThat(Files.exists(clonePath)).isTrue();
    }

    @Test
    void cloningViaPosterSourceEventIdProducesAnIndependentPosterFile() throws IOException {
        byte[] bytes = "contenuto-locandina-di-origine".getBytes();
        MockMultipartFile originalUpload = new MockMultipartFile("poster", "locandina.jpg", "image/jpeg", bytes);
        Event original = service.create(request(), originalUpload, 1L);
        original.setId(42L);
        when(eventRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(original));

        Event clone = service.create(requestWithPosterSource(42L), null, 1L);

        assertThat(clone.getPosterImageUrl()).isNotNull();
        assertThat(clone.getPosterImageUrl()).isNotEqualTo(original.getPosterImageUrl());
        Path originalPath = storageService.resolve(original.getPosterImageUrl());
        Path clonePath = storageService.resolve(clone.getPosterImageUrl());
        assertThat(Files.exists(originalPath)).isTrue();
        assertThat(Files.exists(clonePath)).isTrue();
        assertThat(Files.readAllBytes(clonePath)).isEqualTo(bytes);

        // Indipendenza: cancellare il clone non tocca l'originale.
        storageService.delete(clone.getPosterImageUrl());
        assertThat(Files.exists(clonePath)).isFalse();
        assertThat(Files.exists(originalPath)).isTrue();
    }

    @Test
    void cloningViaPosterSourceEventIdAlsoCopiesTheHeroFocusPoint() throws IOException {
        byte[] bytes = "contenuto-locandina-di-origine".getBytes();
        MockMultipartFile originalUpload = new MockMultipartFile("poster", "locandina.jpg", "image/jpeg", bytes);
        Event original = service.create(request(), originalUpload, 1L);
        original.setId(55L);
        original.setHeroFocusX(30);
        original.setHeroFocusY(70);
        when(eventRepository.findByIdAndDeletedAtIsNull(55L)).thenReturn(Optional.of(original));

        Event clone = service.create(requestWithPosterSource(55L), null, 1L);

        assertThat(clone.getHeroFocusX()).isEqualTo(30);
        assertThat(clone.getHeroFocusY()).isEqualTo(70);
    }

    @Test
    void uploadedFileWinsOverPosterSourceEventId() throws IOException {
        byte[] originalBytes = "locandina-originale".getBytes();
        MockMultipartFile originalUpload = new MockMultipartFile("poster", "locandina.jpg", "image/jpeg", originalBytes);
        Event original = service.create(request(), originalUpload, 1L);
        original.setId(7L);
        when(eventRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(original));

        byte[] uploadedBytes = "locandina-caricata-direttamente".getBytes();
        MockMultipartFile uploaded = new MockMultipartFile("poster", "nuova.jpg", "image/jpeg", uploadedBytes);
        Event created = service.create(requestWithPosterSource(7L), uploaded, 1L);

        Path createdPath = storageService.resolve(created.getPosterImageUrl());
        assertThat(Files.readAllBytes(createdPath)).isEqualTo(uploadedBytes);
        // posterSourceEventId viene ignorato quando arriva un file: nessuna lettura dell'origine avviene.
        verify(eventRepository, never()).findByIdAndDeletedAtIsNull(7L);
    }

    @Test
    void rejectedCreationWithPosterSourceEventIdLeavesNoFileOnDisk() throws IOException {
        byte[] bytes = "locandina-di-origine".getBytes();
        MockMultipartFile originalUpload = new MockMultipartFile("poster", "locandina.jpg", "image/jpeg", bytes);
        Event original = service.create(request(), originalUpload, 1L);
        original.setId(99L);
        when(eventRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(original));

        // Finestra di prenotazione impossibile: apertura dopo la chiusura.
        EventUpsertRequest invalidRequest = new EventUpsertRequest(
                "Saggio di fine anno", null, LocalDateTime.of(2027, 1, 9, 20, 0), "Teatro Comunale",
                null, null, null,
                LocalDateTime.of(2027, 1, 5, 10, 0), LocalDateTime.of(2027, 1, 1, 10, 0),
                null, null, null, null, null, 99L, null, null);

        Path postersDir = uploadDir.resolve(StorageService.POSTERS_DIR);
        long filesBefore;
        try (Stream<Path> listing = Files.list(postersDir)) {
            filesBefore = listing.count();
        }

        assertThatThrownBy(() -> service.create(invalidRequest, null, 1L))
                .isInstanceOf(BadRequestException.class);

        try (Stream<Path> listing = Files.list(postersDir)) {
            assertThat(listing.count()).isEqualTo(filesBefore);
        }
    }
}

package it.abc.musical.services;

import it.abc.musical.dto.AdminEventDtos.EventUpsertRequest;
import it.abc.musical.entities.Event;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    @BeforeEach
    void setUp() throws IOException {
        EventRepository eventRepository = mock(EventRepository.class);
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
        return new EventUpsertRequest(
                "Saggio di fine anno", null, LocalDateTime.of(2027, 1, 9, 20, 0), "Teatro Comunale",
                null, null, null, null, null, null, null, null, null, null);
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
}

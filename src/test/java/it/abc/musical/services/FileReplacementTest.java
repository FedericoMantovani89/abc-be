package it.abc.musical.services;

import it.abc.musical.dto.AdminEventDtos.EventUpsertRequest;
import it.abc.musical.dto.AdminShowDtos.ShowUpsertRequest;
import it.abc.musical.entities.Event;
import it.abc.musical.entities.Show;
import it.abc.musical.entities.ShowImage;
import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.repositories.EventRepository;
import it.abc.musical.repositories.EventTypeRepository;
import it.abc.musical.repositories.ShowRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sostituzione di locandine e immagini: il file vecchio si toglie dal disco solo a transazione
 * confermata, mai prima di aver validato il nuovo. La transazione e' simulata con la
 * sincronizzazione di Spring (quella che usa @Transactional), senza database.
 */
class FileReplacementTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};
    private static final byte[] PDF = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path uploadDir;

    private StorageService storageService;
    private EventRepository eventRepository;
    private ShowRepository showRepository;
    private EventService eventService;
    private ShowService showService;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(uploadDir.toString());
        storageService.init();
        eventRepository = mock(EventRepository.class);
        showRepository = mock(ShowRepository.class);
        eventService = new EventService(eventRepository, mock(EventTypeRepository.class), showRepository,
                storageService, new FileValidationService());
        showService = new ShowService(showRepository, storageService);
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(showRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private String existingFile(UploadTargetType target) throws IOException {
        String path = storageService.store(new MockMultipartFile("f", "vecchia.jpg", "image/jpeg", JPEG), target);
        assertThat(Files.exists(storageService.resolve(path))).isTrue();
        return path;
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void commit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationUtils.invokeAfterCommit(synchronizations);
        TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations, TransactionSynchronization.STATUS_COMMITTED);
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void rollback() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations, TransactionSynchronization.STATUS_ROLLED_BACK);
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static EventUpsertRequest eventRequest() {
        return new EventUpsertRequest(
                "Saggio di fine anno", null, LocalDateTime.of(2027, 1, 9, 20, 0), "Teatro Comunale",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private static ShowUpsertRequest showRequest(List<Long> retainImageIds, String posterPath) {
        return new ShowUpsertRequest(
                "Il Piccolo Principe", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, retainImageIds, posterPath, null, null, null, null, null, null, null);
    }

    @Test
    void deleteAfterCommitKeepsFileUntilCommit() throws IOException {
        String path = existingFile(UploadTargetType.SHOW_POSTER);

        beginTransaction();
        storageService.deleteAfterCommit(path);
        assertThat(Files.exists(storageService.resolve(path))).isTrue();
        commit();

        assertThat(Files.exists(storageService.resolve(path))).isFalse();
    }

    @Test
    void deleteAfterCommitKeepsFileOnRollback() throws IOException {
        String path = existingFile(UploadTargetType.SHOW_POSTER);

        beginTransaction();
        storageService.deleteAfterCommit(path);
        rollback();

        assertThat(Files.exists(storageService.resolve(path))).isTrue();
    }

    @Test
    void rejectedNewEventPosterLeavesOldPosterOnDisk() throws IOException {
        String oldPoster = existingFile(UploadTargetType.SHOW_POSTER);
        Event event = new Event();
        event.setId(5L);
        event.setPosterImageUrl(oldPoster);
        when(eventRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(event));
        // Un PDF con estensione .jpg: il controllo del contenuto lo rifiuta.
        MockMultipartFile fake = new MockMultipartFile("poster", "nuova.jpg", "image/jpeg", PDF);

        beginTransaction();
        assertThatThrownBy(() -> eventService.update(5L, eventRequest(), fake, 1L))
                .isInstanceOf(BadRequestException.class);
        rollback();

        assertThat(Files.exists(storageService.resolve(oldPoster))).isTrue();
    }

    @Test
    void replacedEventPosterIsDeletedOnlyAfterCommit() throws IOException {
        String oldPoster = existingFile(UploadTargetType.SHOW_POSTER);
        Event event = new Event();
        event.setId(6L);
        event.setPosterImageUrl(oldPoster);
        when(eventRepository.findByIdAndDeletedAtIsNull(6L)).thenReturn(Optional.of(event));

        beginTransaction();
        Event updated = eventService.update(6L, eventRequest(),
                new MockMultipartFile("poster", "nuova.jpg", "image/jpeg", JPEG), 1L);
        assertThat(updated.getPosterImageUrl()).isNotEqualTo(oldPoster);
        assertThat(Files.exists(storageService.resolve(oldPoster))).isTrue();
        commit();

        assertThat(Files.exists(storageService.resolve(oldPoster))).isFalse();
        assertThat(Files.exists(storageService.resolve(updated.getPosterImageUrl()))).isTrue();
    }

    @Test
    void retainImageIdsDeletesDroppedImagesOnlyAfterCommit() throws IOException {
        String kept = existingFile(UploadTargetType.SHOW_GALLERY_IMAGE);
        String dropped = existingFile(UploadTargetType.SHOW_GALLERY_IMAGE);
        Show show = new Show();
        show.setId(3L);
        show.getImages().add(image(show, 1L, kept));
        show.getImages().add(image(show, 2L, dropped));
        when(showRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(show));

        beginTransaction();
        showService.update(3L, showRequest(List.of(1L), null), 1L);
        assertThat(show.getImages()).extracting(ShowImage::getId).containsExactly(1L);
        assertThat(Files.exists(storageService.resolve(dropped))).isTrue();
        commit();

        assertThat(Files.exists(storageService.resolve(dropped))).isFalse();
        assertThat(Files.exists(storageService.resolve(kept))).isTrue();
    }

    @Test
    void retainImageIdsKeepsFilesWhenTransactionRollsBack() throws IOException {
        String dropped = existingFile(UploadTargetType.SHOW_GALLERY_IMAGE);
        Show show = new Show();
        show.setId(4L);
        show.getImages().add(image(show, 2L, dropped));
        when(showRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(show));

        beginTransaction();
        showService.update(4L, showRequest(List.of(), null), 1L);
        rollback();

        assertThat(Files.exists(storageService.resolve(dropped))).isTrue();
    }

    @Test
    void replacedShowPosterIsDeletedOnlyAfterCommit() throws IOException {
        String oldPoster = existingFile(UploadTargetType.SHOW_POSTER);
        String newPoster = existingFile(UploadTargetType.SHOW_POSTER);
        Show show = new Show();
        show.setId(8L);
        show.setPosterImageUrl(oldPoster);
        when(showRepository.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.of(show));

        beginTransaction();
        showService.update(8L, showRequest(null, newPoster), 1L);
        assertThat(Files.exists(storageService.resolve(oldPoster))).isTrue();
        commit();

        assertThat(Files.exists(storageService.resolve(oldPoster))).isFalse();
        assertThat(Files.exists(storageService.resolve(newPoster))).isTrue();
    }

    private static ShowImage image(Show show, Long id, String path) {
        ShowImage image = new ShowImage();
        image.setId(id);
        image.setShow(show);
        image.setImageUrl(path);
        return image;
    }
}

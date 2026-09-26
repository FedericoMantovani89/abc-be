package it.abc.musical.services;

import it.abc.musical.dto.UploadDtos.CompleteUploadResponse;
import it.abc.musical.dto.UploadDtos.InitUploadResponse;
import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.util.TempFileMultipartFile;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upload a chunk: sessioni in-memory, scrittura idempotente per indice di chunk,
 * validazione/spostamento in storage definitivo alla chiusura (complete), cleanup
 * schedulato delle sessioni abbandonate. Nessun resume dopo riavvio del backend
 * o chiusura del client (fuori scope, vedi design doc).
 */
@Service
public class ChunkedUploadService {

    private record Session(UUID id, UploadTargetType targetType, String originalFilename,
                            long totalSize, Path tempFile, Instant createdAt) {
    }

    private final StorageService storageService;
    private final FileValidationService fileValidationService;
    private final Path tmpDir;
    private final int chunkSizeBytes;
    private final long sessionTtlHours;

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ChunkedUploadService(StorageService storageService,
                                 FileValidationService fileValidationService,
                                 @Value("${app.upload.chunk-size-mb:8}") int chunkSizeMb,
                                 @Value("${app.upload.session-ttl-hours:24}") long sessionTtlHours) {
        this.storageService = storageService;
        this.fileValidationService = fileValidationService;
        this.chunkSizeBytes = chunkSizeMb * 1024 * 1024;
        this.sessionTtlHours = sessionTtlHours;
        this.tmpDir = storageService.getRoot().resolve("tmp");
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(tmpDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile creare la directory temporanea upload: " + tmpDir, e);
        }
    }

    public InitUploadResponse init(UploadTargetType targetType, String filename, long totalSize) {
        fileValidationService.checkAllowed(filename, totalSize, targetType);
        if (tmpDir.toFile().getUsableSpace() < totalSize) {
            throw new BadRequestException("Spazio disco insufficiente per completare l'upload");
        }

        UUID uploadId = UUID.randomUUID();
        Path tempFile = tmpDir.resolve(uploadId + ".part");
        try {
            // Non pre-allocare a totalSize: il file deve crescere solo con i chunk
            // effettivamente scritti, altrimenti in complete() la lunghezza combacerebbe
            // sempre con totalSize anche con chunk mancanti, rendendo inutile il controllo.
            Files.createFile(tempFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile creare il file temporaneo di upload", e);
        }
        sessions.put(uploadId, new Session(uploadId, targetType, filename, totalSize, tempFile, Instant.now()));
        return new InitUploadResponse(uploadId, chunkSizeBytes);
    }

    public void writeChunk(UUID uploadId, int index, byte[] data) {
        Session session = sessionOf(uploadId);
        long offset = (long) index * chunkSizeBytes;
        if (offset < 0 || offset + data.length > session.totalSize()) {
            throw new BadRequestException("Chunk fuori dai limiti dichiarati per l'upload");
        }
        try (RandomAccessFile raf = new RandomAccessFile(session.tempFile().toFile(), "rw")) {
            raf.seek(offset);
            raf.write(data);
        } catch (IOException e) {
            throw new UncheckedIOException("Scrittura chunk fallita", e);
        }
    }

    public CompleteUploadResponse complete(UUID uploadId) {
        Session session = sessionOf(uploadId);
        File tempFile = session.tempFile().toFile();
        if (tempFile.length() != session.totalSize()) {
            throw new BadRequestException(
                    "Upload incompleto: dimensione ricevuta non corrisponde a quella dichiarata");
        }
        TempFileMultipartFile adapted = new TempFileMultipartFile(tempFile, session.originalFilename(), null);
        String mimeType = fileValidationService.validate(adapted, session.targetType());
        String path = storageService.store(adapted, session.targetType());

        sessions.remove(uploadId);
        storageService.deleteQuietly(session.tempFile());
        return new CompleteUploadResponse(path, mimeType, session.totalSize(), session.originalFilename());
    }

    public void cancel(UUID uploadId) {
        Session session = sessions.remove(uploadId);
        if (session != null) {
            storageService.deleteQuietly(session.tempFile());
        }
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(sessionTtlHours, ChronoUnit.HOURS);
        sessions.values().removeIf(session -> {
            boolean expired = session.createdAt().isBefore(cutoff);
            if (expired) {
                storageService.deleteQuietly(session.tempFile());
            }
            return expired;
        });
        cleanupOrphanedTempFiles(cutoff);
    }

    /** Rimuove i file .part rimasti su disco senza una sessione in memoria (es. dopo un riavvio). */
    private void cleanupOrphanedTempFiles(Instant cutoff) {
        File[] files = tmpDir.toFile().listFiles((dir, name) -> name.endsWith(".part"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            boolean tracked = sessions.values().stream()
                    .anyMatch(s -> s.tempFile().toFile().equals(file));
            if (!tracked && Instant.ofEpochMilli(file.lastModified()).isBefore(cutoff)) {
                storageService.deleteQuietly(file.toPath());
            }
        }
    }

    private Session sessionOf(UUID uploadId) {
        Session session = sessions.get(uploadId);
        if (session == null) {
            throw new NotFoundException("Sessione di upload non trovata o scaduta");
        }
        return session;
    }
}

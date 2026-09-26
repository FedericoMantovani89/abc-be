package it.abc.musical.services;

import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.exceptions.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Salvataggio file su filesystem sotto app.upload-dir, una sottocartella per ogni
 * {@link UploadTargetType}. I path restituiti sono relativi e iniziano con "/" (es. "/posters/abc.jpg").
 */
@Slf4j
@Service
public class StorageService {

    private static final Pattern MANAGED_FILE_PATTERN =
            Pattern.compile("^/[a-z_]+/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[a-z0-9]+$");

    private final Path root;

    public StorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            for (UploadTargetType target : UploadTargetType.values()) {
                Files.createDirectories(root.resolve(target.subdir()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile creare la directory upload: " + root, e);
        }
    }

    public String store(MultipartFile file, UploadTargetType target) {
        String fileName = newFileName(extensionOf(file.getOriginalFilename()));
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, newTarget(target, fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Salvataggio file fallito", e);
        }
        return "/" + target.subdir() + "/" + fileName;
    }

    /**
     * Copia un file gia' gestito (path nella forma restituita da store()) in un file nuovo con
     * nome nuovo, nello stesso stile di store(): usata per clonare una locandina senza far
     * transitare i byte dal client.
     */
    public String copy(String sourceRelativePath, UploadTargetType target) {
        Path source = resolve(sourceRelativePath);
        String fileName = newFileName(extensionOf(sourceRelativePath));
        try {
            Files.copy(source, newTarget(target, fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Copia file fallita", e);
        }
        return "/" + target.subdir() + "/" + fileName;
    }

    /** Risolve un path relativo (es. "/media/x.pdf") nel file assoluto su disco. */
    public Path resolve(String relativePath) {
        String clean = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return confined(root.resolve(clean));
    }

    /**
     * Cancella il file quando la transazione in corso viene confermata; se la transazione si
     * annulla il file resta, insieme alla riga che lo cita. Senza transazione cancella subito.
     * E' l'unico modo di togliere un file sostituito (locandina, immagine della galleria).
     */
    public void deleteAfterCommit(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(relativePath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete(relativePath);
            }
        });
    }

    /** Cancellazione immediata; per sostituire un file in una transazione usare deleteAfterCommit. */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        deleteQuietly(resolve(relativePath));
    }

    /** Cancella un file se esiste; un errore finisce nel log e non interrompe l'operazione. */
    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Eliminazione file fallita: {}", path, e);
        }
    }

    public Path getRoot() {
        return root;
    }

    /**
     * Verifica che il path sia nella forma prodotta da store(): /{subdir}/{uuid}.{estensione}.
     * Usato dagli endpoint di attach (locandina, gallery, documenti) per rifiutare path che non
     * corrispondono a un file effettivamente prodotto da un upload completato.
     */
    public void validateManagedPath(String relativePath, UploadTargetType target) {
        if (relativePath == null || !MANAGED_FILE_PATTERN.matcher(relativePath).matches()
                || !relativePath.startsWith("/" + target.subdir() + "/")) {
            throw new BadRequestException("Path non valido: " + relativePath);
        }
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String newFileName(String extension) {
        return UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
    }

    private Path newTarget(UploadTargetType target, String fileName) {
        return confined(root.resolve(target.subdir()).resolve(fileName));
    }

    private Path confined(Path path) {
        Path normalized = path.normalize();
        if (!normalized.startsWith(root)) {
            throw new BadRequestException("Path non valido");
        }
        return normalized;
    }
}

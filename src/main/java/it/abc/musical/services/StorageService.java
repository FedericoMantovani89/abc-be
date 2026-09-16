package it.abc.musical.services;

import it.abc.musical.exceptions.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
 * Salvataggio file su filesystem sotto app.upload-dir.
 * I path restituiti sono relativi e iniziano con "/" (es. "/posters/abc.jpg").
 */
@Slf4j
@Service
public class StorageService {

    public static final String POSTERS_DIR = "posters";
    public static final String GALLERY_DIR = "show_gallery";
    public static final String MEDIA_DIR = "media";

    private static final Pattern MANAGED_FILE_PATTERN =
            Pattern.compile("^/[a-z_]+/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[a-z0-9]+$");

    private final Path root;

    public StorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root.resolve(POSTERS_DIR));
            Files.createDirectories(root.resolve(GALLERY_DIR));
            Files.createDirectories(root.resolve(MEDIA_DIR));
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile creare la directory upload: " + root, e);
        }
    }

    public String store(MultipartFile file, String subdir) {
        String extension = extensionOf(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path target = root.resolve(subdir).resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path non valido");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Salvataggio file fallito", e);
        }
        return "/" + subdir + "/" + fileName;
    }

    /** Risolve un path relativo (es. "/media/x.pdf") nel file assoluto su disco. */
    public Path resolve(String relativePath) {
        String clean = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        Path target = root.resolve(clean).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path non valido");
        }
        return target;
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.warn("Eliminazione file fallita: {}", relativePath, e);
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
    public void validateManagedPath(String relativePath, String expectedSubdir) {
        if (relativePath == null || !MANAGED_FILE_PATTERN.matcher(relativePath).matches()
                || !relativePath.startsWith("/" + expectedSubdir + "/")) {
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
}

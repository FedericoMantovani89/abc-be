package it.abc.musical.services;

import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.util.FileTypeUtil;
import it.abc.musical.util.FileTypeUtil.Category;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validazione upload: whitelist estensioni, limiti per categoria,
 * MIME detection con Tika e controllo MIME/estensione.
 */
@Service
public class FileValidationService {

    private static final Map<Category, DataSize> SIZE_LIMITS = Map.of(
            Category.VIDEO, DataSize.ofMegabytes(100),
            Category.AUDIO, DataSize.ofMegabytes(50),
            Category.DOCUMENT, DataSize.ofMegabytes(10),
            Category.IMAGE, DataSize.ofMegabytes(5));

    /** Sopra questa soglia Tika analizza solo i magic bytes iniziali. */
    private static final long FULL_SCAN_THRESHOLD = DataSize.ofMegabytes(5).toBytes();
    private static final int MAGIC_BYTES = 8192;

    private static final Set<String> DANGEROUS_MIMES = Set.of(
            "application/x-msdownload", "application/x-dosexec", "application/x-executable",
            "application/x-sh", "text/x-shellscript",
            "application/x-php", "text/x-php",
            "text/x-perl", "application/x-perl",
            "text/x-python", "application/x-python",
            "application/java-archive",
            "application/x-bat", "application/x-msdos-program",
            "application/javascript", "text/javascript");

    private final Tika tika = new Tika();

    /** Valida e restituisce il MIME rilevato. */
    public String validate(MultipartFile file) {
        String extension = StorageService.extensionOf(file.getOriginalFilename());
        if (!FileTypeUtil.isAllowedExtension(extension)) {
            throw new BadRequestException("Tipo di file .%s non consentito. Estensioni ammesse: %s"
                    .formatted(extension, FileTypeUtil.allowedExtensionsList()));
        }

        Category category = FileTypeUtil.categoryOf(extension);
        DataSize limit = SIZE_LIMITS.get(category);
        if (file.getSize() > limit.toBytes()) {
            double sizeMb = file.getSize() / 1024.0 / 1024.0;
            throw new BadRequestException(String.format(Locale.ITALY,
                    "File troppo grande: %s pesa %.2f MB, oltre il limite di %d MB per %s. "
                            + "Riduci le dimensioni del file e riprova.",
                    file.getOriginalFilename(), sizeMb, limit.toMegabytes(), FileTypeUtil.labelOf(category)));
        }

        String detectedMime = detectMime(file);

        if (DANGEROUS_MIMES.contains(detectedMime)) {
            throw new BadRequestException(
                    ("Contenuto del file non consentito: rilevato come %s, non ammesso per motivi di sicurezza. "
                            + "Carica un file diverso.").formatted(detectedMime));
        }
        Set<String> expected = FileTypeUtil.expectedMimes(extension);
        if (!expected.contains(detectedMime)) {
            throw new BadRequestException(
                    "Il contenuto del file non corrisponde all'estensione ." + extension);
        }
        return detectedMime;
    }

    /** Rileva il MIME reale di un file già presente su disco, per ri-verificare metadata
     *  forniti dal client invece di fidarsene ciecamente. */
    public String detectMimeType(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return tika.detect(in);
        } catch (IOException e) {
            throw new BadRequestException("File illeggibile: " + path);
        }
    }

    private String detectMime(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            if (file.getSize() < FULL_SCAN_THRESHOLD) {
                return tika.detect(in);
            }
            byte[] header = in.readNBytes(MAGIC_BYTES);
            return tika.detect(header);
        } catch (IOException e) {
            throw new BadRequestException("File illeggibile");
        }
    }
}

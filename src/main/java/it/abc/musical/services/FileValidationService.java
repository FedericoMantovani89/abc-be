package it.abc.musical.services;

import it.abc.musical.enums.UploadTargetType;
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
import java.util.stream.Collectors;

/**
 * Validazione upload: whitelist estensioni, categorie per destinazione, limiti per categoria,
 * MIME detection con Tika e controllo MIME/estensione.
 */
@Service
public class FileValidationService {

    private static final Map<Category, DataSize> SIZE_LIMITS = Map.of(
            Category.VIDEO, DataSize.ofMegabytes(100),
            Category.AUDIO, DataSize.ofMegabytes(50),
            Category.DOCUMENT, DataSize.ofMegabytes(10),
            Category.IMAGE, DataSize.ofMegabytes(5));

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

    /**
     * Regola unica "questo file e' ammesso per questa destinazione": estensione, categoria della
     * destinazione e limite di dimensione. Si chiama all'avvio del caricamento a pezzi (prima che
     * arrivino i byte), alla sua chiusura e nel caricamento diretto della locandina evento.
     */
    public void checkAllowed(String filename, long size, UploadTargetType target) {
        String extension = StorageService.extensionOf(filename);
        if (!FileTypeUtil.isAllowedExtension(extension)) {
            throw new BadRequestException("Tipo di file .%s non consentito. Estensioni ammesse: %s"
                    .formatted(extension, FileTypeUtil.allowedExtensionsList()));
        }
        Category category = FileTypeUtil.categoryOf(extension);
        if (!target.allowedCategories().contains(category)) {
            String allowedLabels = target.allowedCategories().stream()
                    .sorted()
                    .map(FileTypeUtil::labelOf)
                    .collect(Collectors.joining(", "));
            throw new BadRequestException(
                    "Tipo di file .%s non consentito per questa destinazione: sono ammessi solo file di tipo %s."
                            .formatted(extension, allowedLabels));
        }
        if (size <= 0) {
            throw new BadRequestException("Dimensione file non valida");
        }
        DataSize limit = SIZE_LIMITS.get(category);
        if (size > limit.toBytes()) {
            double sizeMb = size / 1024.0 / 1024.0;
            throw new BadRequestException(String.format(Locale.ITALY,
                    "File troppo grande: %s pesa %.2f MB, oltre il limite di %d MB per %s. "
                            + "Riduci le dimensioni del file e riprova.",
                    filename, sizeMb, limit.toMegabytes(), FileTypeUtil.labelOf(category)));
        }
    }

    /** Regola unica (checkAllowed) piu' il controllo del contenuto reale; restituisce il MIME rilevato. */
    public String validate(MultipartFile file, UploadTargetType target) {
        checkAllowed(file.getOriginalFilename(), file.getSize(), target);
        String extension = StorageService.extensionOf(file.getOriginalFilename());
        String detectedMime;
        try (InputStream in = file.getInputStream()) {
            detectedMime = detect(in);
        } catch (IOException e) {
            throw new BadRequestException("File illeggibile");
        }

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

    /** Rileva il MIME reale di un file già presente su disco, con la stessa regola di validate(). */
    public String detectMimeType(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return detect(in);
        } catch (IOException e) {
            throw new BadRequestException("File illeggibile");
        }
    }

    /**
     * Unico punto in cui si riconosce il tipo reale: Tika guarda solo l'inizio del file (legge
     * al massimo quanto gli serve per le firme e poi riavvolge), quindi va bene per qualsiasi
     * dimensione e da' lo stesso risultato per lo stesso contenuto, da qualunque parte arrivi.
     */
    private String detect(InputStream in) throws IOException {
        return tika.detect(in);
    }
}

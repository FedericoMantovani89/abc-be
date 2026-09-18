package it.abc.musical.util;

import it.abc.musical.enums.MediaFileType;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Mappatura estensione → categoria, MIME attesi e tipo media. */
public final class FileTypeUtil {

    public enum Category {
        IMAGE, AUDIO, VIDEO, DOCUMENT
    }

    private static final Map<String, Category> CATEGORY_BY_EXTENSION = Map.ofEntries(
            Map.entry("jpg", Category.IMAGE),
            Map.entry("jpeg", Category.IMAGE),
            Map.entry("png", Category.IMAGE),
            Map.entry("mp3", Category.AUDIO),
            Map.entry("wav", Category.AUDIO),
            Map.entry("mp4", Category.VIDEO),
            Map.entry("pdf", Category.DOCUMENT),
            Map.entry("doc", Category.DOCUMENT),
            Map.entry("docx", Category.DOCUMENT),
            Map.entry("xls", Category.DOCUMENT),
            Map.entry("xlsx", Category.DOCUMENT),
            Map.entry("zip", Category.DOCUMENT),
            Map.entry("rar", Category.DOCUMENT));

    private static final Map<String, Set<String>> EXPECTED_MIMES = Map.ofEntries(
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("mp3", Set.of("audio/mpeg", "audio/mp3")),
            Map.entry("wav", Set.of("audio/vnd.wave", "audio/x-wav", "audio/wav")),
            Map.entry("mp4", Set.of("video/mp4", "video/quicktime", "application/mp4")),
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of("application/msword", "application/x-tika-msoffice")),
            Map.entry("docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml", "application/zip")),
            Map.entry("xls", Set.of("application/vnd.ms-excel", "application/x-tika-msoffice")),
            Map.entry("xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/x-tika-ooxml", "application/zip")),
            Map.entry("zip", Set.of("application/zip", "application/x-tika-ooxml")),
            Map.entry("rar", Set.of("application/x-rar-compressed", "application/x-rar")));

    /** Nome della categoria in italiano leggibile, plurale, per i messaggi utente. */
    private static final Map<Category, String> CATEGORY_LABELS_IT = Map.of(
            Category.IMAGE, "immagini",
            Category.AUDIO, "audio",
            Category.VIDEO, "video",
            Category.DOCUMENT, "documenti");

    private FileTypeUtil() {
    }

    public static boolean isAllowedExtension(String extension) {
        return CATEGORY_BY_EXTENSION.containsKey(extension);
    }

    public static Category categoryOf(String extension) {
        return CATEGORY_BY_EXTENSION.get(extension);
    }

    public static Set<String> expectedMimes(String extension) {
        return EXPECTED_MIMES.getOrDefault(extension, Set.of());
    }

    /** Nome della categoria in italiano leggibile, plurale (es. "immagini"), per i messaggi utente. */
    public static String labelOf(Category category) {
        return CATEGORY_LABELS_IT.get(category);
    }

    /** Elenco ordinato delle estensioni ammesse (es. ".doc, .jpg, .pdf, ..."), per i messaggi utente. */
    public static String allowedExtensionsList() {
        return new TreeSet<>(CATEGORY_BY_EXTENSION.keySet()).stream()
                .map(ext -> "." + ext)
                .collect(Collectors.joining(", "));
    }

    public static MediaFileType mediaTypeOf(String extension) {
        Category category = CATEGORY_BY_EXTENSION.get(extension);
        if (category == null) {
            return MediaFileType.DOCUMENT;
        }
        return switch (category) {
            case AUDIO -> MediaFileType.AUDIO;
            case VIDEO -> MediaFileType.VIDEO;
            case IMAGE, DOCUMENT -> MediaFileType.DOCUMENT;
        };
    }
}

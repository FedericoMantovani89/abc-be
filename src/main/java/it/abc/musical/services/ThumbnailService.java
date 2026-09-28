package it.abc.musical.services;

import it.abc.musical.entities.Document;
import it.abc.musical.enums.MediaFileType;
import it.abc.musical.util.FileTypeUtil;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Anteprime JPEG ridotte di immagini e video dell'archivio, generate alla prima richiesta e
 * tenute su disco in {upload-dir}/thumbs/{uuid}-{w}.jpg. Nessun campo sul documento: il file in
 * cache e' l'unico stato. Un file illeggibile non produce un errore ma un'anteprima assente.
 */
@Slf4j
@Service
public class ThumbnailService {

    public static final int DEFAULT_WIDTH = 480;
    public static final int LARGE_WIDTH = 1600;
    /** Qualsiasi valore di w diverso da 480 e 1600. */
    public static final int FALLBACK_WIDTH = 400;

    private static final float JPEG_QUALITY = 0.8f;
    /** ffmpeg -q:v va da 2 (migliore) a 31: 4 corrisponde circa alla qualita' 0.8. */
    private static final String FFMPEG_QUALITY = "4";
    private static final long FFMPEG_TIMEOUT_SECONDS = 20;
    /** Oltre questi pixel l'immagine non si decodifica (un PNG di 30000x30000 occupa GB in memoria). */
    private static final long MAX_IMAGE_PIXELS = 80_000_000L;

    private final StorageService storageService;
    private final String ffmpeg;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    /** Chiavi gia' fallite da quando gira il processo: un video rovinato non tiene occupato ffmpeg a ogni richiesta. */
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

    public ThumbnailService(StorageService storageService,
                            @Value("${app.thumbs.ffmpeg:ffmpeg}") String ffmpeg) {
        this.storageService = storageService;
        this.ffmpeg = ffmpeg;
    }

    /** Larghezza effettiva per il parametro w: assente = 480, 480 o 1600 restano, tutto il resto = 400. */
    public static int normalizeWidth(Integer requested) {
        if (requested == null) {
            return DEFAULT_WIDTH;
        }
        return requested == DEFAULT_WIDTH || requested == LARGE_WIDTH ? requested : FALLBACK_WIDTH;
    }

    /**
     * Il file dell'anteprima, generato ora se non c'e' ancora; vuoto se il documento non e' ne'
     * immagine ne' video o se la generazione fallisce.
     */
    public Optional<Path> thumbnail(Document document, int width) {
        Kind kind = kindOf(document);
        if (kind == null) {
            return Optional.empty();
        }
        String key = document.getUuid() + "-" + width;
        Path target = storageService.getRoot().resolve("thumbs").resolve(key + ".jpg");
        if (Files.isRegularFile(target)) {
            return Optional.of(target);
        }
        if (failed.contains(key)) {
            return Optional.empty();
        }
        // Due richieste della stessa anteprima: la seconda aspetta e trova il file della prima.
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            try {
                if (Files.isRegularFile(target)) {
                    return Optional.of(target);
                }
                if (failed.contains(key)) {
                    return Optional.empty();
                }
                Path source = storageService.resolve(document.getFilePath());
                if (generate(kind, source, target, width)) {
                    return Optional.of(target);
                }
                failed.add(key);
                return Optional.empty();
            } finally {
                locks.remove(key);
            }
        }
    }

    private static Kind kindOf(Document document) {
        if (document.getMediaType() == MediaFileType.VIDEO) {
            return Kind.VIDEO;
        }
        FileTypeUtil.Category category =
                FileTypeUtil.categoryOf(StorageService.extensionOf(document.getFilePath()));
        if (category == FileTypeUtil.Category.IMAGE) {
            return Kind.IMAGE;
        }
        return category == FileTypeUtil.Category.VIDEO ? Kind.VIDEO : null;
    }

    private boolean generate(Kind kind, Path source, Path target, int width) {
        Path tmp = null;
        try {
            Files.createDirectories(target.getParent());
            tmp = Files.createTempFile(target.getParent(), ".tmp-", ".jpg");
            boolean ok = kind == Kind.IMAGE
                    ? imageThumbnail(source, tmp, width)
                    : videoThumbnail(source, tmp, width);
            if (!ok || Files.size(tmp) == 0) {
                return false;
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
            return true;
        } catch (Exception | OutOfMemoryError e) {
            log.warn("Anteprima non generata per {}: {}", source.getFileName(), e.toString());
            return false;
        } finally {
            if (tmp != null) {
                storageService.deleteQuietly(tmp);
            }
        }
    }

    /**
     * Thumbnailator applica l'orientamento EXIF dei JPEG. Il lato lungo scende a width solo se
     * e' piu' grande (mai ingrandire); la trasparenza dei PNG diventa sfondo bianco.
     */
    private static boolean imageThumbnail(Path source, Path out, int width) throws IOException {
        if (!withinPixelLimit(source)) {
            return false;
        }
        BufferedImage oriented = Thumbnails.of(source.toFile()).scale(1.0).asBufferedImage();
        BufferedImage resized = Math.max(oriented.getWidth(), oriented.getHeight()) > width
                ? Thumbnails.of(oriented).size(width, width).asBufferedImage()
                : oriented;
        BufferedImage rgb = new BufferedImage(resized.getWidth(), resized.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(resized, 0, 0, null);
        } finally {
            g.dispose();
        }
        Thumbnails.of(rgb).scale(1.0).outputFormat("jpg").outputQuality(JPEG_QUALITY).toFile(out.toFile());
        return true;
    }

    /** Legge solo l'intestazione: false se l'immagine e' troppo grande o il formato e' sconosciuto. */
    static boolean withinPixelLimit(Path source) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(source.toFile())) {
            if (in == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return (long) reader.getWidth(0) * reader.getHeight(0) <= MAX_IMAGE_PIXELS;
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Fotogramma a 1 s; se il video e' piu' corto ffmpeg non produce nulla e si ripiega sul primo.
     * Lo scale riduce solo (min con la dimensione originale) e tiene le proporzioni. Un ffmpeg
     * oltre il tempo massimo viene ucciso e non si riprova.
     */
    private boolean videoThumbnail(Path source, Path out, int width) throws IOException, InterruptedException {
        return runFfmpeg(source, out, width, "1") || runFfmpeg(source, out, width, "0");
    }

    private boolean runFfmpeg(Path source, Path out, int width, String seek)
            throws IOException, InterruptedException {
        Files.deleteIfExists(out);
        String scale = "scale='min(iw,%d)':'min(ih,%d)':force_original_aspect_ratio=decrease"
                .formatted(width, width);
        List<String> command = List.of(ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error",
                "-ss", seek, "-i", source.toString(),
                "-frames:v", "1", "-an", "-vf", scale, "-q:v", FFMPEG_QUALITY,
                "-f", "image2", "-y", out.toString());
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ffmpeg oltre " + FFMPEG_TIMEOUT_SECONDS + " s: processo ucciso");
        }
        return process.exitValue() == 0 && Files.isRegularFile(out) && Files.size(out) > 0;
    }

    private enum Kind { IMAGE, VIDEO }
}

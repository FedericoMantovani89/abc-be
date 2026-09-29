package it.abc.musical.services;

import it.abc.musical.entities.Show;
import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.repositories.ShowRepository;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Locandine per le email di verifica e reimpostazione password: gia' sfumate verso il colore della
 * testata (#0F0F23), cosi' nella mail basta un {@code <img>} con misure fisse (niente gradienti CSS,
 * che Outlook e Gmail non mostrano). Stesso disegno di bozze-email-2026-09-28/v2-src/sfuma.py.
 * <p>
 * Generate alla prima richiesta e tenute in {upload-dir}/email-posters/. La chiave del file contiene
 * spettacolo, variante, nome e data di modifica della locandina: una locandina sostituita produce
 * un file nuovo e quelli vecchi dello stesso spettacolo e variante vengono cancellati.
 */
@Slf4j
@Service
public class EmailPosterService {

    /** Colore della testata della mail (#0F0F23): la locandina sfuma in questo. */
    private static final int DARK = 0x0F0F23;
    /** Fondo della pagina della mail (#F1F0F7): fuori dagli angoli arrotondati. */
    private static final int PAGE = 0xF1F0F7;
    private static final float JPEG_QUALITY = 0.82f;
    private static final String CACHE_DIR = "email-posters";

    /**
     * Le due immagini della mail, in pixel doppi rispetto alla misura mostrata (schermi ad alta
     * densita'): SIDE e' la colonna a destra della testata (220x293 a video), BAND la fascia in
     * alto sul telefono (351x130 a video).
     */
    public enum Variant {
        SIDE(440, 586),
        BAND(702, 260);

        public final int width;
        public final int height;

        Variant(int width, int height) {
            this.width = width;
            this.height = height;
        }

        /** "side" o "band", maiuscole ignorate; qualsiasi altro valore e' una richiesta non valida. */
        public static Variant parse(String value) {
            if (value != null) {
                for (Variant v : values()) {
                    if (v.name().equalsIgnoreCase(value)) {
                        return v;
                    }
                }
            }
            throw new BadRequestException("email.locandina.variante.non.valida");
        }

        public String param() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ShowRepository showRepository;
    private final StorageService storageService;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public EmailPosterService(ShowRepository showRepository, StorageService storageService) {
        this.showRepository = showRepository;
        this.storageService = storageService;
    }

    /** Uno spettacolo a caso fra quelli non cancellati con locandina; vuoto se non ce n'e' nessuno. */
    public Optional<Long> randomShowId() {
        List<Long> ids = showRepository.findIdsWithPosterAndNotDeleted();
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
    }

    /**
     * Il JPEG sfumato della locandina dello spettacolo; vuoto se lo spettacolo non esiste, e'
     * cancellato, non ha locandina o la locandina non si legge. Il file su disco viene solo dal
     * percorso salvato nello spettacolo, controllato come percorso gestito di /posters.
     */
    public Optional<Path> poster(long showId, Variant variant) {
        Optional<Path> source = showRepository.findByIdAndDeletedAtIsNull(showId)
                .map(Show::getPosterImageUrl)
                .flatMap(this::posterFile);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        Path poster = source.get();
        long modified;
        try {
            modified = Files.getLastModifiedTime(poster).toMillis();
        } catch (IOException e) {
            return Optional.empty();
        }
        String prefix = showId + "-" + variant.param() + "-";
        String key = prefix + baseName(poster) + "-" + modified;
        Path dir = storageService.getRoot().resolve(CACHE_DIR);
        Path target = dir.resolve(key + ".jpg");
        if (Files.isRegularFile(target)) {
            return Optional.of(target);
        }
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            try {
                if (Files.isRegularFile(target)) {
                    return Optional.of(target);
                }
                if (!generate(poster, target, variant)) {
                    return Optional.empty();
                }
                deleteOlder(dir, prefix, target);
                return Optional.of(target);
            } finally {
                locks.remove(key);
            }
        }
    }

    private Optional<Path> posterFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }
        try {
            storageService.validateManagedPath(relativePath, UploadTargetType.SHOW_POSTER);
            Path file = storageService.resolve(relativePath);
            return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Locandina con percorso non gestito, ignorata: {}", relativePath);
            return Optional.empty();
        }
    }

    private boolean generate(Path source, Path target, Variant variant) {
        Path tmp = null;
        try {
            if (!ThumbnailService.withinPixelLimit(source)) {
                return false;
            }
            Files.createDirectories(target.getParent());
            tmp = Files.createTempFile(target.getParent(), ".tmp-", ".jpg");
            // Thumbnailator applica l'orientamento EXIF.
            BufferedImage oriented = Thumbnails.of(source.toFile()).scale(1.0).asBufferedImage();
            BufferedImage faded = render(oriented, variant);
            Thumbnails.of(faded).scale(1.0).outputFormat("jpg").outputQuality(JPEG_QUALITY).toFile(tmp.toFile());
            if (Files.size(tmp) == 0) {
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
            log.warn("Locandina per email non generata da {}: {}", source.getFileName(), e.toString());
            return false;
        } finally {
            if (tmp != null) {
                storageService.deleteQuietly(tmp);
            }
        }
    }

    /**
     * Ritaglio alle proporzioni della variante, dissolvenza verso #0F0F23 e angoli arrotondati:
     * SIDE sfuma sul 55% sinistro e sul 18% in basso, angolo in alto a destra arrotondato come la
     * testata; BAND sfuma sul 55% in basso, angoli in alto arrotondati.
     */
    static BufferedImage render(BufferedImage source, Variant variant) throws IOException {
        int w = variant.width;
        int h = variant.height;
        BufferedImage fitted = Thumbnails.of(crop(source, w, h, variant == Variant.SIDE ? 0.35 : 0.3))
                .forceSize(w, h).asBufferedImage();
        int radius = 16;
        int fadeLeft = variant == Variant.SIDE ? (int) (w * 0.55) : 0;
        int fadeBottom = (int) (h * (variant == Variant.SIDE ? 0.18 : 0.55));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double a = 1.0;
                if (x < fadeLeft) {
                    a *= smooth((double) x / fadeLeft);
                }
                if (y > h - fadeBottom) {
                    a *= smooth((double) (h - y) / fadeBottom);
                }
                int rgb = blend(DARK, fitted.getRGB(x, y), a);
                boolean outside = outsideCorner(w - radius, true, x, y, radius)
                        || (variant == Variant.BAND && outsideCorner(radius, false, x, y, radius));
                out.setRGB(x, y, outside ? PAGE : rgb);
            }
        }
        return out;
    }

    /** Come ImageOps.fit di Pillow: il riquadro piu' grande con le proporzioni w:h, centrato in orizzontale. */
    private static BufferedImage crop(BufferedImage src, int w, int h, double centerY) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        double target = (double) w / h;
        int cw = sw;
        int ch = sh;
        if ((double) sw / sh > target) {
            cw = (int) Math.round(sh * target);
        } else {
            ch = (int) Math.round(sw / target);
        }
        int cx = (int) Math.round((sw - cw) * 0.5);
        int cy = (int) Math.round((sh - ch) * centerY);
        return src.getSubimage(cx, cy, Math.max(1, cw), Math.max(1, ch));
    }

    /** Pixel nell'angolo in alto (destro o sinistro) ma fuori dal quarto di cerchio di centro (cx, radius). */
    private static boolean outsideCorner(int cx, boolean right, int x, int y, int radius) {
        boolean inSquare = y < radius && (right ? x > cx : x < cx);
        if (!inSquare) {
            return false;
        }
        double dx = x - cx;
        double dy = y - radius;
        return dx * dx + dy * dy > (double) radius * radius;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static int blend(int background, int foreground, double alpha) {
        int r = mix((background >> 16) & 0xFF, (foreground >> 16) & 0xFF, alpha);
        int g = mix((background >> 8) & 0xFF, (foreground >> 8) & 0xFF, alpha);
        int b = mix(background & 0xFF, foreground & 0xFF, alpha);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int back, int front, double alpha) {
        return (int) Math.round(back + (front - back) * alpha);
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** Toglie le versioni precedenti della stessa locandina e variante. */
    private void deleteOlder(Path dir, String prefix, Path keep) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, prefix + "*.jpg")) {
            for (Path file : files) {
                if (!file.equals(keep)) {
                    storageService.deleteQuietly(file);
                }
            }
        } catch (IOException e) {
            log.warn("Pulizia locandine email non riuscita: {}", e.toString());
        }
    }
}

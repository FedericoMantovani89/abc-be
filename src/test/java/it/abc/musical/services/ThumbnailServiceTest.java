package it.abc.musical.services;

import it.abc.musical.TestImages;
import it.abc.musical.TestPdfs;
import it.abc.musical.entities.Document;
import it.abc.musical.enums.MediaFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Generazione delle anteprime senza Spring. Il test dei video gira solo dove c'e' ffmpeg
 * (nell'immagine Docker si', sulle macchine di sviluppo non sempre).
 */
class ThumbnailServiceTest {

    @TempDir
    Path root;

    ThumbnailService service;

    @BeforeEach
    void setUp() {
        service = new ThumbnailService(new StorageService(root.toString()), "ffmpeg");
    }

    private Document document(String extension, byte[] content, MediaFileType mediaType) throws Exception {
        String relative = "/media/" + UUID.randomUUID() + "." + extension;
        Path file = root.resolve(relative.substring(1));
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        Document document = new Document();
        document.setUuid(UUID.randomUUID());
        document.setFilePath(relative);
        document.setMediaType(mediaType);
        return document;
    }

    private static BufferedImage read(Optional<Path> thumb) throws Exception {
        assertThat(thumb).isPresent();
        byte[] bytes = Files.readAllBytes(thumb.get());
        assertThat(bytes[0] & 0xFF).isEqualTo(0xFF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xD8);
        return TestImages.decode(bytes);
    }

    @Test
    void widthParameterIsNormalized() {
        assertThat(ThumbnailService.normalizeWidth(null)).isEqualTo(480);
        assertThat(ThumbnailService.normalizeWidth(480)).isEqualTo(480);
        assertThat(ThumbnailService.normalizeWidth(1600)).isEqualTo(1600);
        assertThat(ThumbnailService.normalizeWidth(999)).isEqualTo(400);
        assertThat(ThumbnailService.normalizeWidth(400)).isEqualTo(400);
        assertThat(ThumbnailService.normalizeWidth(-1)).isEqualTo(400);
    }

    @Test
    void phonePhotoWithExifOrientation6ComesOutPortrait() throws Exception {
        Document photo = document("jpg", TestImages.jpegWithOrientation(1200, 600, 6), MediaFileType.DOCUMENT);

        BufferedImage thumb = read(service.thumbnail(photo, 480));

        assertThat(thumb.getWidth()).isEqualTo(240);
        assertThat(thumb.getHeight()).isEqualTo(480);
    }

    @Test
    void pngIsReducedToTheRequestedLongSide() throws Exception {
        Document png = document("png", TestImages.png(1000, 500), MediaFileType.DOCUMENT);

        BufferedImage thumb = read(service.thumbnail(png, 400));

        assertThat(thumb.getWidth()).isEqualTo(400);
        assertThat(thumb.getHeight()).isEqualTo(200);
    }

    @Test
    void smallImageIsNeverEnlarged() throws Exception {
        Document small = document("jpeg", TestImages.jpeg(300, 150), MediaFileType.DOCUMENT);

        BufferedImage thumb = read(service.thumbnail(small, 1600));

        assertThat(thumb.getWidth()).isEqualTo(300);
        assertThat(thumb.getHeight()).isEqualTo(150);
    }

    @Test
    void cacheFileIsNamedByUuidAndWidthAndReused() throws Exception {
        Document photo = document("jpg", TestImages.jpeg(800, 600), MediaFileType.DOCUMENT);

        Path first = service.thumbnail(photo, 480).orElseThrow();
        assertThat(first).isEqualTo(root.resolve("thumbs").resolve(photo.getUuid() + "-480.jpg"));
        byte[] marker = {1, 2, 3};
        Files.write(first, marker);

        assertThat(Files.readAllBytes(service.thumbnail(photo, 480).orElseThrow())).isEqualTo(marker);
        try (var leftovers = Files.list(root.resolve("thumbs"))) {
            assertThat(leftovers.map(p -> p.getFileName().toString()))
                    .containsExactly(photo.getUuid() + "-480.jpg");
        }
    }

    @Test
    void documentsWithoutPreviewAndBrokenFilesGiveNothing() throws Exception {
        assertThat(service.thumbnail(document("pdf", TestPdfs.clean(), MediaFileType.DOCUMENT), 480)).isEmpty();
        assertThat(service.thumbnail(document("mp3", new byte[] {1, 2, 3}, MediaFileType.AUDIO), 480)).isEmpty();
        assertThat(service.thumbnail(document("jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, 0, 1}, MediaFileType.DOCUMENT), 480)).isEmpty();
        assertThat(service.thumbnail(document("png", "non sono un png".getBytes(), MediaFileType.DOCUMENT), 480)).isEmpty();
        try (var leftovers = Files.list(root.resolve("thumbs"))) {
            assertThat(leftovers).isEmpty();
        }
    }

    @Test
    void missingFfmpegGivesNothing() throws Exception {
        ThumbnailService withoutFfmpeg =
                new ThumbnailService(new StorageService(root.toString()), "ffmpeg-che-non-esiste");
        Document video = document("mp4", new byte[] {0, 0, 0, 0x18}, MediaFileType.VIDEO);

        assertThat(withoutFfmpeg.thumbnail(video, 480)).isEmpty();
    }

    @Test
    void videoFrameAtOneSecondOrFirstFrameOfShortVideo() throws Exception {
        assumeTrue(ffmpegAvailable(), "ffmpeg non presente su questa macchina");
        Document longVideo = document("mp4", testVideo(3.0), MediaFileType.VIDEO);
        Document shortVideo = document("mp4", testVideo(0.4), MediaFileType.VIDEO);
        Document brokenVideo = document("mp4", new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p'}, MediaFileType.VIDEO);

        BufferedImage frame = read(service.thumbnail(longVideo, 480));
        assertThat(frame.getWidth()).isEqualTo(480);
        assertThat(frame.getHeight()).isEqualTo(270);

        BufferedImage notEnlarged = read(service.thumbnail(longVideo, 1600));
        assertThat(notEnlarged.getWidth()).isEqualTo(640);
        assertThat(notEnlarged.getHeight()).isEqualTo(360);

        assertThat(read(service.thumbnail(shortVideo, 480)).getWidth()).isEqualTo(480);
        assertThat(service.thumbnail(brokenVideo, 480)).isEmpty();
    }

    private static boolean ffmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] testVideo(double seconds) throws Exception {
        Path out = root.resolve("sorgente-" + UUID.randomUUID() + ".mp4");
        Process p = new ProcessBuilder(List.of("ffmpeg", "-nostdin", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc=size=640x360:rate=25:duration=" + seconds,
                "-pix_fmt", "yuv420p", "-y", out.toString()))
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).isZero();
        return Files.readAllBytes(out);
    }
}

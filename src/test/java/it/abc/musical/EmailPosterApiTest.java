package it.abc.musical;

import it.abc.musical.entities.Show;
import it.abc.musical.repositories.ShowRepository;
import it.abc.musical.services.EmailPosterService;
import it.abc.musical.services.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/public/email/poster.jpg: locandina sfumata per le email, senza login. */
class EmailPosterApiTest extends IntegrationTestBase {

    @Autowired
    ShowRepository showRepository;

    @Autowired
    StorageService storageService;

    @Autowired
    EmailPosterService emailPosterService;

    private Show show(String posterPath, boolean deleted) {
        Show show = new Show();
        show.setTitle("Locandina email " + UUID.randomUUID());
        show.setPosterImageUrl(posterPath);
        if (deleted) {
            show.setDeletedAt(LocalDateTime.now());
        }
        return showRepository.save(show);
    }

    private String posterFile(byte[] content) throws Exception {
        String relative = "/posters/" + UUID.randomUUID() + ".jpg";
        Files.write(storageService.resolve(relative), content);
        return relative;
    }

    private BufferedImage fetch(long showId, String variant) throws Exception {
        byte[] body = mockMvc.perform(get("/api/public/email/poster.jpg")
                        .param("show", String.valueOf(showId)).param("v", variant))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=2592000, public"))
                .andReturn().getResponse().getContentAsByteArray();
        return TestImages.decode(body);
    }

    private long cachedFiles(long showId) throws Exception {
        Path dir = storageService.getRoot().resolve("email-posters");
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().startsWith(showId + "-")).count();
        }
    }

    @Test
    void sideAndBandHaveTheEmailSizesWithoutLogin() throws Exception {
        Show show = show(posterFile(TestImages.jpeg(300, 400)), false);

        BufferedImage side = fetch(show.getId(), "side");
        BufferedImage band = fetch(show.getId(), "band");

        assertThat(side.getWidth()).isEqualTo(440);
        assertThat(side.getHeight()).isEqualTo(586);
        assertThat(band.getWidth()).isEqualTo(702);
        assertThat(band.getHeight()).isEqualTo(260);
    }

    @Test
    void imageIsCachedOnDiskAndRenewedWhenThePosterChanges() throws Exception {
        String poster = posterFile(TestImages.jpeg(300, 400));
        Show show = show(poster, false);

        fetch(show.getId(), "side");
        Path first = emailPosterService.poster(show.getId(), EmailPosterService.Variant.SIDE).orElseThrow();
        FileTime generated = Files.getLastModifiedTime(first);
        fetch(show.getId(), "side");
        assertThat(Files.getLastModifiedTime(first)).isEqualTo(generated);
        assertThat(cachedFiles(show.getId())).isEqualTo(1);

        // Locandina riscritta: data di modifica nuova, file nuovo in cache, il vecchio sparisce.
        Path source = storageService.resolve(poster);
        Files.write(source, TestImages.jpeg(600, 800));
        Files.setLastModifiedTime(source, FileTime.fromMillis(Files.getLastModifiedTime(source).toMillis() + 5000));
        fetch(show.getId(), "side");
        Path second = emailPosterService.poster(show.getId(), EmailPosterService.Variant.SIDE).orElseThrow();
        assertThat(second).isNotEqualTo(first);
        assertThat(first).doesNotExist();
        assertThat(cachedFiles(show.getId())).isEqualTo(1);
    }

    @Test
    void missingDeletedOrPosterlessShowsAre404() throws Exception {
        long deleted = show(posterFile(TestImages.jpeg(300, 400)), true).getId();
        long noPoster = show(null, false).getId();
        long fileGone = show("/posters/" + UUID.randomUUID() + ".jpg", false).getId();
        long notManaged = show("/posters/../media/x.jpg", false).getId();

        for (long id : new long[] {deleted, noPoster, fileGone, notManaged, 999_999_999L}) {
            mockMvc.perform(get("/api/public/email/poster.jpg").param("show", String.valueOf(id)).param("v", "side"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void invalidParametersAre400() throws Exception {
        long id = show(posterFile(TestImages.jpeg(300, 400)), false).getId();

        mockMvc.perform(get("/api/public/email/poster.jpg").param("show", String.valueOf(id)).param("v", "big"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/email/poster.jpg").param("show", String.valueOf(id)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/email/poster.jpg").param("show", "../posters/x").param("v", "side"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/email/poster.jpg").param("v", "side"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void randomChoiceSkipsDeletedAndPosterlessShows() throws Exception {
        long withPoster = show(posterFile(TestImages.jpeg(300, 400)), false).getId();
        long deleted = show(posterFile(TestImages.jpeg(300, 400)), true).getId();
        long noPoster = show(null, false).getId();
        long blankPoster = show("", false).getId();

        assertThat(showRepository.findIdsWithPosterAndNotDeleted())
                .contains(withPoster)
                .doesNotContain(deleted, noPoster, blankPoster);
    }
}

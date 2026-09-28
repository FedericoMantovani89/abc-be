package it.abc.musical;

import com.jayway.jsonpath.JsonPath;
import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.services.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/member/file/{uuid}/thumb: stessa regola d'accesso del file, JPEG ridotto, 404 per
 * i documenti senza anteprima.
 * <p>
 * Ogni test lavora sotto una propria cartella radice dal nome univoco, cancellata alla fine:
 * il database e' condiviso con le altre classi (ArchiveApiTest conta le cartelle in radice).
 */
class ThumbnailApiTest extends IntegrationTestBase {

    @Autowired
    StorageService storageService;

    Long rootId;

    @BeforeEach
    void createRoot() throws Exception {
        rootId = createFolder("Anteprime " + UUID.randomUUID(), null);
    }

    @AfterEach
    void deleteRoot() throws Exception {
        mockMvc.perform(delete("/api/admin/media/folder/" + rootId).with(asRole("ADMIN")));
    }

    private Long createFolder(String name, Long parentId) throws Exception {
        String body = parentId == null
                ? "{\"name\": \"" + name + "\"}"
                : "{\"name\": \"" + name + "\", \"parentFolderId\": " + parentId + "}";
        return id(mockMvc.perform(post("/api/admin/media/folder").with(asRole("ADMIN"))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String attach(Long folderId, String filename, byte[] content) throws Exception {
        String path = upload(UploadTargetType.MEDIA_DOCUMENT, filename, content);
        String json = mockMvc.perform(post("/api/admin/media").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"filePath": "%s", "originalFilename": "%s", "folderId": %d}
                                """.formatted(path, filename, folderId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.uuid");
    }

    private BufferedImage thumb(String uuid, String role, String w) throws Exception {
        var request = get("/api/member/file/" + uuid + "/thumb").with(asRole(role));
        if (w != null) {
            request.param("w", w);
        }
        byte[] body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=604800, private"))
                .andReturn().getResponse().getContentAsByteArray();
        return TestImages.decode(body);
    }

    @Test
    void phonePhotoIsServedUprightAndReduced() throws Exception {
        String uuid = attach(rootId, "foto.jpg", TestImages.jpegWithOrientation(1200, 600, 6));

        BufferedImage image = thumb(uuid, "MEMBER", null);

        assertThat(image.getWidth()).isEqualTo(240);
        assertThat(image.getHeight()).isEqualTo(480);
    }

    @Test
    void widthIs480By1600OrElse400AndSmallImagesAreNotEnlarged() throws Exception {
        String big = attach(rootId, "grande.png", TestImages.png(2000, 1000));
        String small = attach(rootId, "piccola.png", TestImages.png(300, 150));

        assertThat(thumb(big, "MEMBER", "1600").getWidth()).isEqualTo(1600);
        assertThat(thumb(big, "MEMBER", "480").getWidth()).isEqualTo(480);
        assertThat(thumb(big, "MEMBER", "999").getWidth()).isEqualTo(400);
        assertThat(thumb(big, "MEMBER", "abc").getWidth()).isEqualTo(400);
        assertThat(thumb(small, "MEMBER", "1600").getWidth()).isEqualTo(300);
        assertThat(thumb(small, "ADMIN", "1600").getHeight()).isEqualTo(150);
    }

    @Test
    void documentWithoutPreviewIs404() throws Exception {
        String pdf = attach(rootId, "copione.pdf", TestPdfs.clean());

        mockMvc.perform(get("/api/member/file/" + pdf + "/thumb").with(asRole("MEMBER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/member/file/" + UUID.randomUUID() + "/thumb").with(asRole("MEMBER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void sameAccessRuleAsTheFile() throws Exception {
        Long staffOnly = createFolder("Regia", rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + staffOnly + "/permissions").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"allowedRoles\": [\"STAFF\"]}"))
                .andExpect(status().isNoContent());
        String uuid = attach(staffOnly, "riservata.jpg", TestImages.jpeg(800, 600));

        mockMvc.perform(get("/api/member/file/" + uuid).with(asRole("MEMBER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/member/file/" + uuid + "/thumb").with(asRole("MEMBER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/member/file/" + uuid + "/thumb"))
                .andExpect(status().isUnauthorized());
        assertThat(thumb(uuid, "STAFF", null).getWidth()).isEqualTo(480);
    }

    @Test
    void secondRequestIsServedFromTheDiskCache() throws Exception {
        String uuid = attach(rootId, "cache.jpg", TestImages.jpeg(800, 600));
        thumb(uuid, "MEMBER", null);
        Path cached = storageService.getRoot().resolve("thumbs").resolve(uuid + "-480.jpg");
        assertThat(cached).isRegularFile();
        // Se la seconda richiesta rigenerasse, questi byte verrebbero sovrascritti.
        byte[] marker = TestImages.jpeg(10, 10);
        Files.write(cached, marker);

        byte[] second = mockMvc.perform(get("/api/member/file/" + uuid + "/thumb").with(asRole("MEMBER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(second).isEqualTo(marker);
    }
}

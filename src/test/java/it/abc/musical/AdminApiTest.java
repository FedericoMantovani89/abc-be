package it.abc.musical;

import it.abc.musical.services.StorageService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Autorizzazioni per ruolo sulle API admin/member e filtro targetRoles. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminApiTest extends IntegrationTestBase {

    @Autowired
    StorageService storageService;

    @Test
    @Order(1)
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/member/communications")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void memberCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(asRole("MEMBER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/shows").with(asRole("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void staffCanManageContentButNotUsers() throws Exception {
        mockMvc.perform(get("/api/admin/shows").with(asRole("STAFF")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").with(asRole("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void communicationTargetRolesFilterApplies() throws Exception {
        mockMvc.perform(post("/api/admin/communications").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Per tutti", "content": "Ciao a tutti"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/communications").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Solo staff", "content": "Riservata",
                                 "targetRoles": "STAFF,DIRECTOR"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/member/communications").with(asRole("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Per tutti"));

        mockMvc.perform(get("/api/member/communications").with(asRole("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Order(5)
    void publicShowsAreOpen() throws Exception {
        mockMvc.perform(get("/api/public/shows")).andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void createShowWithPosterPathThenAttachGalleryImage() throws Exception {
        String createResponse = mockMvc.perform(post("/api/admin/shows").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Spettacolo di prova", "posterPath": "/posters/550e8400-e29b-41d4-a716-446655440000.jpg"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        Long showId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.id")).longValue();

        mockMvc.perform(get("/api/admin/shows/" + showId).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterImageUrl").value("/posters/550e8400-e29b-41d4-a716-446655440000.jpg"));

        mockMvc.perform(post("/api/admin/shows/" + showId + "/images").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"imagePath": "/show_gallery/550e8400-e29b-41d4-a716-446655440001.jpg", "caption": "Prova"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageId").exists());
    }

    @Test
    @Order(7)
    void attachDocumentFromUploadedPath() throws Exception {
        Path mediaFile = storageService.resolve("/media/550e8400-e29b-41d4-a716-446655440002.pdf");
        Files.createDirectories(mediaFile.getParent());
        Files.write(mediaFile, "%PDF-1.4\n%%EOF".getBytes());

        // mimeType/fileSizeBytes sono facoltativi e ignorati: il server li ri-deriva dal
        // file su disco (vedi asserzione su $.mimeType sotto, che verifica proprio questa provenienza).
        mockMvc.perform(post("/api/admin/media").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"filePath": "/media/550e8400-e29b-41d4-a716-446655440002.pdf", "originalFilename": "regolamento.pdf",
                                 "mimeType": "application/octet-stream", "fileSizeBytes": 1,
                                 "folderId": null}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("regolamento.pdf"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"));
    }

    /**
     * La lista admin degli spettacoli e' l'unico endpoint che restituisce una Page.
     * Con spring.data.web.pageable.serialization-mode=via-dto la Page viene serializzata
     * come PagedModel: i metadati stanno sotto "page", non piu' sparsi in cima.
     */
    @Test
    @Order(8)
    void adminShowsListIsSerializedAsPagedModel() throws Exception {
        mockMvc.perform(get("/api/admin/shows").param("page", "0").param("size", "20")
                        .with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].title").exists())
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                .andExpect(jsonPath("$.page.totalPages").isNumber())
                // la forma vecchia (PageImpl piatta) non deve piu' comparire
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist())
                .andExpect(jsonPath("$.number").doesNotExist())
                .andExpect(jsonPath("$.size").doesNotExist())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist());
    }

    /**
     * Round-trip del punto focale della locandina (hero_focus_x/y, V005): conferma che
     * Flyway ha applicato la migration (colonne esistenti, entity/DTO allineati) e che i
     * CHECK sul range e sulla coppia sono applicati anche a livello di validazione service.
     */
    @Test
    @Order(9)
    void heroFocusPointRoundTripThenRejectsInvalidPair() throws Exception {
        String createResponse = mockMvc.perform(post("/api/admin/shows").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Spettacolo con punto focale", "heroFocusX": 50, "heroFocusY": 15}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        Long showId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.id")).longValue();

        mockMvc.perform(get("/api/admin/shows/" + showId).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroFocusX").value(50))
                .andExpect(jsonPath("$.heroFocusY").value(15));

        mockMvc.perform(post("/api/admin/shows").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Punto focale incompleto", "heroFocusX": 50, "heroFocusY": null}
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * Round-trip dello zoom della locandina in hero (hero_zoom_desktop/mobile, V006):
     * conferma che Flyway ha applicato la migration e che il range 10..300 e' applicato
     * a livello di validazione service (i due valori sono indipendenti, a differenza
     * del punto focale).
     */
    @Test
    @Order(10)
    void heroZoomRoundTripThenRejectsInvalidValue() throws Exception {
        String createResponse = mockMvc.perform(post("/api/admin/shows").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Spettacolo con zoom locandina", "heroZoomDesktop": 80, "heroZoomMobile": 60}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        Long showId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.id")).longValue();

        mockMvc.perform(get("/api/admin/shows/" + showId).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroZoomDesktop").value(80))
                .andExpect(jsonPath("$.heroZoomMobile").value(60));

        mockMvc.perform(post("/api/admin/shows").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Zoom non valido", "heroZoomDesktop": 5}
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * Round-trip del secondo punto focale della locandina per mobile
     * (hero_focus_mobile_x/y, V007): punto desktop e punto mobile diversi fra loro
     * (nessun vincolo incrociato), stesso CHECK di coppia del punto desktop (V005)
     * applicato anche lato validazione service.
     */
    @Test
    @Order(11)
    void heroFocusMobileRoundTripWithDifferentDesktopPointThenRejectsInvalidPair() throws Exception {
        String createResponse = mockMvc.perform(post("/api/admin/shows").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Spettacolo con punto focale mobile", "heroFocusX": 50, "heroFocusY": 15,
                                 "heroFocusMobileX": 80, "heroFocusMobileY": 40}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        Long showId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.id")).longValue();

        mockMvc.perform(get("/api/admin/shows/" + showId).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroFocusX").value(50))
                .andExpect(jsonPath("$.heroFocusY").value(15))
                .andExpect(jsonPath("$.heroFocusMobileX").value(80))
                .andExpect(jsonPath("$.heroFocusMobileY").value(40));

        mockMvc.perform(post("/api/admin/shows").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Punto focale mobile incompleto", "heroFocusMobileX": 80, "heroFocusMobileY": null}
                                """))
                .andExpect(status().isBadRequest());
    }
}

package it.abc.musical;

import it.abc.musical.services.StorageService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Autorizzazioni per ruolo sulle API admin/member e filtro targetRoles. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StorageService storageService;

    @MockitoBean
    JavaMailSender mailSender;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asRole(String role) {
        return jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))
                .jwt(j -> j.subject("test@abc.it"));
    }

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

        // mimeType/fileSizeBytes restano nel body solo perché DocumentAttachRequest li
        // dichiara @NotBlank/@NotNull (DTO invariato, fuori scope per questo fix) — il
        // valore inviato è ignorato: il server li ri-deriva dal file su disco (vedi
        // asserzione su $.mimeType sotto, che verifica proprio questa provenienza).
        mockMvc.perform(post("/api/admin/media").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"filePath": "/media/550e8400-e29b-41d4-a716-446655440002.pdf", "originalFilename": "regolamento.pdf",
                                 "mimeType": "application/octet-stream", "fileSizeBytes": 1,
                                 "documentCategory": "OTHER", "visibility": "MEMBERS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("regolamento.pdf"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"));
    }
}

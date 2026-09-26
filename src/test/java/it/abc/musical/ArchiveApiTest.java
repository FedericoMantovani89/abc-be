package it.abc.musical;

import com.jayway.jsonpath.JsonPath;
import it.abc.musical.repositories.AuditLogRepository;
import it.abc.musical.services.StorageService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Archivio documenti: i permessi sono solo i ruoli delle cartelle, ereditati da tutte le
 * cartelle superiori, con la stessa regola per l'albero e per il download.
 *
 * Struttura costruita dai test: "Regia" (solo STAFF) > "Libera" (nessun ruolo) > copione.pdf;
 * "Pubblica" (nessun ruolo) > avviso.pdf.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ArchiveApiTest extends IntegrationTestBase {

    @Autowired
    StorageService storageService;

    @Autowired
    AuditLogRepository auditLogRepository;

    static Long regiaId;
    static Long liberaId;
    static String copioneUuid;
    static String avvisoUuid;

    private Long createFolder(String name, Long parentId) throws Exception {
        String body = parentId == null
                ? "{\"name\": \"" + name + "\"}"
                : "{\"name\": \"" + name + "\", \"parentFolderId\": " + parentId + "}";
        String json = mockMvc.perform(post("/api/admin/media/folder").with(asRole("ADMIN"))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private String attachPdf(Long folderId, String originalFilename) throws Exception {
        String path = "/media/" + UUID.randomUUID() + ".pdf";
        Path file = storageService.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String json = mockMvc.perform(post("/api/admin/media").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"filePath": "%s", "originalFilename": "%s", "folderId": %d}
                                """.formatted(path, originalFilename.replace("\"", "\\\""), folderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.documentCategory").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.uuid");
    }

    @Test
    @Order(1)
    void setUpFoldersAndPermissions() throws Exception {
        regiaId = createFolder("Regia", null);
        liberaId = createFolder("Libera", regiaId);
        Long pubblicaId = createFolder("Pubblica", null);

        mockMvc.perform(patch("/api/admin/media/folder/" + regiaId + "/permissions").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("{\"allowedRoles\": [\" staff \", \"\"]}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(get("/api/admin/media/folder/" + regiaId + "/permissions").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedRoles.length()").value(1))
                .andExpect(jsonPath("$.allowedRoles[0]").value("STAFF"));
        mockMvc.perform(get("/api/admin/media/folder/" + liberaId + "/permissions").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedRoles.length()").value(0));

        copioneUuid = attachPdf(liberaId, "copione.pdf");
        avvisoUuid = attachPdf(pubblicaId, "avviso \"importante\" è qui.pdf");
    }

    /**
     * media/ non e' fra le cartelle pubbliche di SecurityConfig (derivate da
     * UploadTargetType.publiclyServed(), come posters/ e show_gallery/): un percorso diretto
     * non passa da WebMvcConfig (nessun resource handler la serve) e cade nella regola di
     * default "tutto il resto richiede autenticazione", quindi risponde 401 anche per un file
     * che esiste davvero su disco. L'unico modo di leggerlo resta MemberFileController, che
     * controlla i permessi della cartella.
     */
    @Test
    @Order(2)
    void mediaFolderIsNeverServedAsAStaticResource() throws Exception {
        String path = "/media/" + UUID.randomUUID() + ".pdf";
        Path file = storageService.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void unknownRoleIsRejected() throws Exception {
        mockMvc.perform(patch("/api/admin/media/folder/" + regiaId + "/permissions").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("{\"allowedRoles\": [\"REGISTA_CAPO\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void memberCannotReachDocumentInFreeSubfolderOfStaffFolder() throws Exception {
        // Albero: "Regia" e tutto cio' che contiene spariscono; "Pubblica" resta.
        mockMvc.perform(get("/api/member/documents").with(asRole("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders.length()").value(1))
                .andExpect(jsonPath("$.folders[0].name").value("Pubblica"))
                .andExpect(jsonPath("$.folders[0].documents[0].uuid").value(avvisoUuid));
        // Download: stessa regola, anche con il link diretto.
        mockMvc.perform(get("/api/member/file/" + copioneUuid).with(asRole("MEMBER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/member/file/" + avvisoUuid).with(asRole("MEMBER")))
                .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    void staffSeesAndDownloadsDocumentInFreeSubfolder() throws Exception {
        mockMvc.perform(get("/api/member/documents").with(asRole("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders.length()").value(2))
                .andExpect(jsonPath("$.folders[?(@.name == 'Regia')].allowedRoles").value("STAFF"))
                .andExpect(jsonPath("$.folders[?(@.name == 'Regia')].children[0].documents[0].uuid")
                        .value(copioneUuid));
        mockMvc.perform(get("/api/member/file/" + copioneUuid).with(asRole("STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void downloadHeaderSurvivesQuotesAndAccentsInFilename() throws Exception {
        String header = mockMvc.perform(get("/api/member/file/" + avvisoUuid).param("download", "true")
                        .with(asRole("MEMBER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        ContentDisposition parsed = ContentDisposition.parse(header);
        assertThat(parsed.isAttachment()).isTrue();
        assertThat(parsed.getFilename()).isEqualTo("avviso \"importante\" è qui.pdf");
    }

    @Test
    @Order(7)
    void oneAuditRowPerDownloadNotPerRangeRequest() throws Exception {
        long before = downloadRows();

        mockMvc.perform(get("/api/member/file/" + avvisoUuid).with(asRole("MEMBER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/member/file/" + avvisoUuid).header(HttpHeaders.RANGE, "bytes=0-")
                        .with(asRole("MEMBER")))
                .andExpect(status().isPartialContent());
        mockMvc.perform(get("/api/member/file/" + avvisoUuid).header(HttpHeaders.RANGE, "bytes=5-")
                        .with(asRole("MEMBER")))
                .andExpect(status().isPartialContent());

        assertThat(downloadRows() - before).isEqualTo(2);
    }

    private long downloadRows() {
        return auditLogRepository.findAll().stream()
                .filter(a -> "DOWNLOAD".equals(a.getAction()) && "Document".equals(a.getEntityType()))
                .count();
    }

    @Test
    @Order(8)
    void deletingFolderTakesItsDocumentsAlong() throws Exception {
        mockMvc.perform(delete("/api/admin/media/folder/" + regiaId).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/member/file/" + copioneUuid).with(asRole("STAFF")))
                .andExpect(status().isNotFound());
        // Il documento cancellato con la sua cartella (ON DELETE CASCADE, V011) non deve
        // riapparire in radice: non si controlla la lunghezza assoluta di rootDocuments,
        // che con un database condiviso fra classi di test puo' contenere anche documenti
        // di altre classi (es. AdminApiTest ne aggancia uno senza cartella).
        mockMvc.perform(get("/api/admin/media/tree").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders.length()").value(1))
                .andExpect(jsonPath("$.folders[0].name").value("Pubblica"))
                .andExpect(jsonPath("$.rootDocuments[?(@.uuid=='" + copioneUuid + "')]").isEmpty());
    }
}

package it.abc.musical;

import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.repositories.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rinomina di file (titolo mostrato) e cartelle dell'archivio.
 * <p>
 * Ogni test lavora sotto una propria cartella radice dal nome univoco, cancellata alla fine:
 * il database e' condiviso con le altre classi (ArchiveApiTest conta le cartelle in radice).
 */
class MediaRenameApiTest extends IntegrationTestBase {

    private static final String DUPLICATE_SPARTITI =
            "Esiste gia' una cartella «SPARTITI» in quella posizione.";

    @Autowired
    AuditLogRepository auditLogRepository;

    Long rootId;

    @BeforeEach
    void createRoot() throws Exception {
        rootId = createFolder("Rinomina " + UUID.randomUUID(), null);
    }

    @AfterEach
    void deleteRoot() throws Exception {
        mockMvc.perform(delete("/api/admin/media/folder/" + rootId).with(asRole("ADMIN")));
    }

    private Long createFolder(String name, Long parentId) throws Exception {
        String body = parentId == null
                ? "{\"name\": \"" + name + "\"}"
                : "{\"name\": \"" + name + "\", \"parentFolderId\": " + parentId + "}";
        String json = mockMvc.perform(post("/api/admin/media/folder").with(asRole("ADMIN"))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return id(json);
    }

    private Long attachPdf(Long folderId) throws Exception {
        String path = upload(UploadTargetType.MEDIA_DOCUMENT, "copione.pdf", TestPdfs.clean());
        String json = mockMvc.perform(post("/api/admin/media").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"filePath": "%s", "originalFilename": "copione.pdf", "folderId": %d}
                                """.formatted(path, folderId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return id(json);
    }

    private long renameRows(String entityType, Long entityId) {
        return auditLogRepository.findAll().stream()
                .filter(a -> "RENAME".equals(a.getAction()) && entityType.equals(a.getEntityType())
                        && entityId.equals(a.getEntityId()))
                .count();
    }

    private static String titleBody(String title) {
        return "{\"title\": \"" + title + "\"}";
    }

    private static String nameBody(String name) {
        return "{\"name\": \"" + name + "\"}";
    }

    // ------------------------------------------------------------------ documenti

    @Test
    void documentRenameChangesOnlyTheTitle() throws Exception {
        Long doc = attachPdf(rootId);

        mockMvc.perform(patch("/api/admin/media/" + doc).with(asRole("ADMIN"))
                        .contentType("application/json").content(titleBody("  Copione atto primo  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doc))
                .andExpect(jsonPath("$.title").value("Copione atto primo"))
                .andExpect(jsonPath("$.fileName").value("copione.pdf"))
                .andExpect(jsonPath("$.folderId").value(rootId));

        mockMvc.perform(get("/api/admin/media/tree").with(asRole("ADMIN")))
                .andExpect(jsonPath("$..documents[?(@.id == " + doc + ")].title")
                        .value("Copione atto primo"));
        assertThat(renameRows("Document", doc)).isEqualTo(1);
    }

    @Test
    void documentRenameValidatesTitle() throws Exception {
        Long doc = attachPdf(rootId);
        mockMvc.perform(patch("/api/admin/media/" + doc).with(asRole("ADMIN"))
                        .contentType("application/json").content(titleBody("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists());
        mockMvc.perform(patch("/api/admin/media/" + doc).with(asRole("ADMIN"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/media/" + doc).with(asRole("ADMIN"))
                        .contentType("application/json").content(titleBody("x".repeat(256))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/media/" + doc).with(asRole("ADMIN"))
                        .contentType("application/json").content(titleBody("x".repeat(255))))
                .andExpect(status().isOk());
        assertThat(renameRows("Document", doc)).isEqualTo(1);
    }

    @Test
    void documentRenameAnswers404ForMissingOrDeletedDocument() throws Exception {
        Long doc = attachPdf(rootId);
        mockMvc.perform(delete("/api/admin/media/" + doc).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/admin/media/" + doc).with(asRole("ADMIN"))
                        .contentType("application/json").content(titleBody("Nuovo")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("File non trovato"));
        mockMvc.perform(patch("/api/admin/media/999999999").with(asRole("ADMIN"))
                        .contentType("application/json").content(titleBody("Nuovo")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("File non trovato"));
    }

    @Test
    void documentRenameDoesNotShadowDocumentMove() throws Exception {
        Long a = createFolder("A", rootId);
        Long doc = attachPdf(rootId);
        mockMvc.perform(patch("/api/admin/media/" + doc + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"folderId\": " + a + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").value(a))
                .andExpect(jsonPath("$.title").value("copione.pdf"));
        assertThat(renameRows("Document", doc)).isZero();
    }

    @Test
    void memberCannotRename() throws Exception {
        Long doc = attachPdf(rootId);
        mockMvc.perform(patch("/api/admin/media/" + doc).with(asRole("REGISTER"))
                        .contentType("application/json").content(titleBody("Nuovo")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/media/folder/" + rootId).with(asRole("REGISTER"))
                        .contentType("application/json").content(nameBody("Nuovo")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ cartelle

    @Test
    void folderRenameKeepsParentAndContent() throws Exception {
        Long a = createFolder("A", rootId);
        Long inner = createFolder("Interna", a);
        Long doc = attachPdf(a);

        mockMvc.perform(patch("/api/admin/media/folder/" + a).with(asRole("ADMIN"))
                        .contentType("application/json").content(nameBody("  Copioni 2026 ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(a))
                .andExpect(jsonPath("$.name").value("Copioni 2026"))
                .andExpect(jsonPath("$.parentFolderId").value(rootId));

        mockMvc.perform(get("/api/admin/media/tree").with(asRole("ADMIN")))
                .andExpect(jsonPath("$..children[?(@.id == " + a + ")].name").value("Copioni 2026"))
                .andExpect(jsonPath("$..children[?(@.id == " + a + ")].children[0].id").value(inner.intValue()))
                .andExpect(jsonPath("$..children[?(@.id == " + a + ")].documents[0].id").value(doc.intValue()));
        assertThat(renameRows("Folder", a)).isEqualTo(1);
    }

    @Test
    void folderRenameRefusesDuplicateNameCaseInsensitive() throws Exception {
        createFolder("Spartiti", rootId);
        Long a = createFolder("A", rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + a).with(asRole("ADMIN"))
                        .contentType("application/json").content(nameBody(" SPARTITI ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(DUPLICATE_SPARTITI));
        assertThat(renameRows("Folder", a)).isZero();
    }

    @Test
    void folderRenameAllowsSameNameInAnotherParent() throws Exception {
        Long a = createFolder("A", rootId);
        createFolder("Audio", a);
        Long b = createFolder("B", rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + b).with(asRole("ADMIN"))
                        .contentType("application/json").content(nameBody("Audio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Audio"));
    }

    @Test
    void folderRenameChangingOnlyCaseIsAllowed() throws Exception {
        Long a = createFolder("spartiti", rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + a).with(asRole("ADMIN"))
                        .contentType("application/json").content(nameBody("Spartiti")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spartiti"));
    }

    @Test
    void folderRenameValidatesLikeCreate() throws Exception {
        Long a = createFolder("A", rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + a).with(asRole("ADMIN"))
                        .contentType("application/json").content(nameBody("  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
        mockMvc.perform(patch("/api/admin/media/folder/" + a).with(asRole("ADMIN"))
                        .contentType("application/json").content(nameBody("x".repeat(256))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void folderRenameAnswers404ForMissingOrDeletedFolder() throws Exception {
        Long gone = createFolder("Cestinata", rootId);
        mockMvc.perform(delete("/api/admin/media/folder/" + gone).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/admin/media/folder/" + gone).with(asRole("ADMIN"))
                        .contentType("application/json").content(nameBody("Nuova")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cartella non trovata"));
        mockMvc.perform(patch("/api/admin/media/folder/999999999").with(asRole("ADMIN"))
                        .contentType("application/json").content(nameBody("Nuova")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cartella non trovata"));
    }

    // ------------------------------------------------------------------ nessun conflitto di rotte

    @Test
    void folderRenameDoesNotShadowMove() throws Exception {
        Long a = createFolder("A", rootId);
        Long b = createFolder("B", rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + a + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"parentFolderId\": " + b + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("A"))
                .andExpect(jsonPath("$.parentFolderId").value(b));
        assertThat(renameRows("Folder", a)).isZero();
    }

    @Test
    void folderRenameDoesNotShadowPermissions() throws Exception {
        Long a = createFolder("A", rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + a + "/permissions").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"allowedRoles\": []}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/media/folder/" + a + "/permissions").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedRoles").isArray());
        assertThat(renameRows("Folder", a)).isZero();
    }

    @Test
    void folderPathIsNotReadAsADocumentId() throws Exception {
        Long a = createFolder("A", rootId);
        // "folder/{id}" ha due segmenti: va alla cartella, non al PATCH /{id} dei documenti.
        mockMvc.perform(patch("/api/admin/media/folder/" + a).with(asRole("ADMIN"))
                        .contentType("application/json").content(titleBody("Titolo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
    }
}

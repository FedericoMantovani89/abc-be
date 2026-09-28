package it.abc.musical;

import com.jayway.jsonpath.JsonPath;
import it.abc.musical.repositories.AuditLogRepository;
import it.abc.musical.services.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spostamento di file e cartelle dell'archivio e "trova o crea" di una cartella.
 * <p>
 * Ogni test lavora sotto una propria cartella radice dal nome univoco, cancellata alla fine:
 * il database e' condiviso con le altre classi (ArchiveApiTest conta le cartelle in radice).
 */
class MediaMoveApiTest extends IntegrationTestBase {

    private static final String CYCLE_ERROR =
            "Non puoi spostare una cartella dentro se stessa o in una sua sottocartella.";

    @Autowired
    StorageService storageService;

    @Autowired
    AuditLogRepository auditLogRepository;

    Long rootId;

    @BeforeEach
    void createRoot() throws Exception {
        rootId = createFolder("Sposta " + UUID.randomUUID(), null);
    }

    @AfterEach
    void deleteRoot() throws Exception {
        mockMvc.perform(delete("/api/admin/media/folder/" + rootId).with(asRole("ADMIN")));
    }

    private static String folderBody(String name, Long parentId) {
        return parentId == null
                ? "{\"name\": \"" + name + "\"}"
                : "{\"name\": \"" + name + "\", \"parentFolderId\": " + parentId + "}";
    }

    private Long createFolder(String name, Long parentId) throws Exception {
        String json = mockMvc.perform(post("/api/admin/media/folder").with(asRole("ADMIN"))
                        .contentType("application/json").content(folderBody(name, parentId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return id(json);
    }

    private Long attachPdf(Long folderId) throws Exception {
        String path = "/media/" + UUID.randomUUID() + ".pdf";
        Path file = storageService.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String json = mockMvc.perform(post("/api/admin/media").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"filePath": "%s", "originalFilename": "copione.pdf", "folderId": %d}
                                """.formatted(path, folderId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return id(json);
    }

    private long moveRows(String entityType, Long entityId) {
        return auditLogRepository.findAll().stream()
                .filter(a -> "MOVE".equals(a.getAction()) && entityType.equals(a.getEntityType())
                        && entityId.equals(a.getEntityId()))
                .count();
    }

    // ------------------------------------------------------------------ documenti

    @Test
    void documentMovesToAnotherFolderAndToRoot() throws Exception {
        Long a = createFolder("A", rootId);
        Long b = createFolder("B", rootId);
        Long doc = attachPdf(a);

        mockMvc.perform(patch("/api/admin/media/" + doc + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"folderId\": " + b + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doc))
                .andExpect(jsonPath("$.folderId").value(b));

        String tree = mockMvc.perform(get("/api/admin/media/tree").with(asRole("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(tree,
                "$..children[?(@.id == " + b + ")].documents[*].id")).containsExactly(doc.intValue());
        assertThat(JsonPath.<List<Object>>read(tree,
                "$..children[?(@.id == " + a + ")].documents[*].id")).isEmpty();

        mockMvc.perform(patch("/api/admin/media/" + doc + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"folderId\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").doesNotExist());

        assertThat(moveRows("Document", doc)).isEqualTo(2);
        mockMvc.perform(delete("/api/admin/media/" + doc).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void documentMoveAnswers404ForMissingDocumentOrFolder() throws Exception {
        Long doc = attachPdf(rootId);
        Long gone = createFolder("Cestinata", rootId);
        mockMvc.perform(delete("/api/admin/media/folder/" + gone).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/admin/media/" + doc + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"folderId\": " + gone + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cartella non trovata"));
        mockMvc.perform(patch("/api/admin/media/" + doc + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"folderId\": 999999999}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/admin/media/999999999/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"folderId\": null}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("File non trovato"));
    }

    @Test
    void memberCannotMove() throws Exception {
        Long doc = attachPdf(rootId);
        mockMvc.perform(patch("/api/admin/media/" + doc + "/move").with(asRole("REGISTER"))
                        .contentType("application/json").content("{\"folderId\": null}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/media/folder/" + rootId + "/move").with(asRole("REGISTER"))
                        .contentType("application/json").content("{\"parentFolderId\": null}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ cartelle

    @Test
    void folderMovesWithItsContent() throws Exception {
        Long a = createFolder("A", rootId);
        Long b = createFolder("B", rootId);
        Long inner = createFolder("Interna", a);
        Long doc = attachPdf(inner);

        mockMvc.perform(patch("/api/admin/media/folder/" + a + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"parentFolderId\": " + b + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(a))
                .andExpect(jsonPath("$.name").value("A"))
                .andExpect(jsonPath("$.parentFolderId").value(b));

        String tree = mockMvc.perform(get("/api/admin/media/tree").with(asRole("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(tree,
                "$..children[?(@.id == " + b + ")].children[*].id")).containsExactly(a.intValue());
        assertThat(JsonPath.<List<Object>>read(tree,
                "$..children[?(@.id == " + inner + ")].documents[*].id")).containsExactly(doc.intValue());
        assertThat(moveRows("Folder", a)).isEqualTo(1);
    }

    @Test
    void folderMovesToRoot() throws Exception {
        Long a = createFolder("Radice " + UUID.randomUUID(), rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + a + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"parentFolderId\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentFolderId").doesNotExist());
        mockMvc.perform(delete("/api/admin/media/folder/" + a).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void folderCannotMoveIntoItself() throws Exception {
        Long a = createFolder("A", rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + a + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"parentFolderId\": " + a + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(CYCLE_ERROR));
    }

    @Test
    void folderCannotMoveIntoItsGrandchild() throws Exception {
        Long a = createFolder("A", rootId);
        Long child = createFolder("Figlia", a);
        Long grandchild = createFolder("Nipote", child);
        mockMvc.perform(patch("/api/admin/media/folder/" + a + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"parentFolderId\": " + grandchild + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(CYCLE_ERROR));
        assertThat(moveRows("Folder", a)).isZero();
    }

    @Test
    void folderMoveRefusesDuplicateNameCaseInsensitive() throws Exception {
        Long a = createFolder("A", rootId);
        Long b = createFolder("B", rootId);
        createFolder("Copioni", b);
        Long copioni = createFolder("COPIONI", a);
        mockMvc.perform(patch("/api/admin/media/folder/" + copioni + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"parentFolderId\": " + b + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Esiste gia' una cartella «COPIONI» in quella posizione."));
    }

    @Test
    void folderMoveToItsOwnParentIsANoOp() throws Exception {
        Long a = createFolder("A", rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + a + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"parentFolderId\": " + rootId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentFolderId").value(rootId));
    }

    @Test
    void folderMoveAnswers404ForMissingFolderOrParent() throws Exception {
        Long a = createFolder("A", rootId);
        mockMvc.perform(patch("/api/admin/media/folder/" + a + "/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"parentFolderId\": 999999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cartella non trovata"));
        mockMvc.perform(patch("/api/admin/media/folder/999999999/move").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"parentFolderId\": null}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ crea / trova o crea

    @Test
    void createFolderWithDuplicateNameAnswers409() throws Exception {
        createFolder("Spartiti", rootId);
        mockMvc.perform(post("/api/admin/media/folder").with(asRole("ADMIN"))
                        .contentType("application/json").content(folderBody("  spartiti ", rootId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Esiste gia' una cartella «spartiti» in quella posizione."));
    }

    @Test
    void ensureCreatesThenFindsCaseInsensitive() throws Exception {
        String created = mockMvc.perform(post("/api/admin/media/folder/ensure").with(asRole("ADMIN"))
                        .contentType("application/json").content(folderBody("Foto Prove", rootId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Foto Prove"))
                .andExpect(jsonPath("$.parentFolderId").value(rootId))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/admin/media/folder/ensure").with(asRole("ADMIN"))
                        .contentType("application/json").content(folderBody(" FOTO prove ", rootId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id(created)))
                .andExpect(jsonPath("$.name").value("Foto Prove"));
    }

    @Test
    void ensureSameNameInAnotherParentCreatesANewFolder() throws Exception {
        Long a = createFolder("A", rootId);
        Long first = id(mockMvc.perform(post("/api/admin/media/folder/ensure").with(asRole("ADMIN"))
                        .contentType("application/json").content(folderBody("Audio", rootId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String second = mockMvc.perform(post("/api/admin/media/folder/ensure").with(asRole("ADMIN"))
                        .contentType("application/json").content(folderBody("Audio", a)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(id(second)).isNotEqualTo(first);
    }

    @Test
    void ensureIgnoresDeletedFoldersAndValidatesLikeCreate() throws Exception {
        Long old = createFolder("Vecchia", rootId);
        mockMvc.perform(delete("/api/admin/media/folder/" + old).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());
        String json = mockMvc.perform(post("/api/admin/media/folder/ensure").with(asRole("ADMIN"))
                        .contentType("application/json").content(folderBody("Vecchia", rootId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(id(json)).isNotEqualTo(old);

        mockMvc.perform(post("/api/admin/media/folder/ensure").with(asRole("ADMIN"))
                        .contentType("application/json").content(folderBody("  ", rootId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
        mockMvc.perform(post("/api/admin/media/folder/ensure").with(asRole("ADMIN"))
                        .contentType("application/json").content(folderBody("x".repeat(256), rootId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/media/folder/ensure").with(asRole("ADMIN"))
                        .contentType("application/json").content(folderBody("Nuova", 999999999L)))
                .andExpect(status().isNotFound());
    }
}

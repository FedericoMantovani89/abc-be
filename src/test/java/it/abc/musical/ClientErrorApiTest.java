package it.abc.musical;

import com.jayway.jsonpath.JsonPath;
import it.abc.musical.services.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Punto 7 dell'audit: errori causati dal client che oggi arrivano come 500 devono diventare un
 * 4xx appropriato, con un messaggio generico (mai il testo SQL o lo stack trace nella risposta).
 */
class ClientErrorApiTest extends IntegrationTestBase {

    @Autowired
    StorageService storageService;

    @Test
    void unreadableJsonBodyIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(post("/api/admin/calendar-event-types").with(asRole("ADMIN"))
                        .contentType("application/json").content("{questo non e' json valido"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEnumValueInJsonBodyIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(post("/api/admin/uploads").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"targetType": "NON_ESISTE", "filename": "x.jpg", "totalSize": 100}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingRequiredParameterIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(get("/api/auth/verify"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericIdInPathIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(get("/api/admin/shows/non-numerico").with(asRole("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingStaticResourceIsANotFoundNotAServerError() throws Exception {
        mockMvc.perform(get("/posters/questo-file-non-esiste-mai.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedRangeHeaderIsRangeNotSatisfiableNotAServerError() throws Exception {
        String folderJson = mockMvc.perform(post("/api/admin/media/folder").with(asRole("ADMIN"))
                        .contentType("application/json").content("{\"name\": \"ClientErrorApiTest\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long folderId = ((Number) JsonPath.read(folderJson, "$.id")).longValue();

        String path = "/media/" + UUID.randomUUID() + ".pdf";
        Path file = storageService.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String documentJson = mockMvc.perform(post("/api/admin/media").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"filePath": "%s", "originalFilename": "prova.pdf", "folderId": %d}
                                """.formatted(path, folderId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String uuid = JsonPath.read(documentJson, "$.uuid");

        mockMvc.perform(get("/api/member/file/" + uuid).with(asRole("MEMBER"))
                        .header(HttpHeaders.RANGE, "bytes=non-un-range"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }

    @Test
    void databaseConstraintViolationIsAConflictNotAServerError() throws Exception {
        mockMvc.perform(post("/api/admin/calendar-event-types").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"name": "Colore non valido", "colorHex": "zzzzzz"}
                                """))
                .andExpect(status().isConflict());
    }
}

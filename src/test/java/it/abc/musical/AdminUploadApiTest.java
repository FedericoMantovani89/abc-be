package it.abc.musical;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static it.abc.musical.TestFixtures.fakeJpegBytes;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminUploadApiTest extends IntegrationTestBase {

    @Test
    @Order(1)
    void uploadEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/uploads").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void memberCannotInitiateUpload() throws Exception {
        mockMvc.perform(post("/api/admin/uploads").with(asRole("MEMBER"))
                        .contentType("application/json")
                        .content("""
                                {"targetType": "SHOW_POSTER", "filename": "poster.jpg", "totalSize": 10}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void fullChunkedUploadFlowAssemblesAndValidatesFile() throws Exception {
        byte[] content = fakeJpegBytes(20);

        String initResponse = mockMvc.perform(post("/api/admin/uploads").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"targetType": "SHOW_POSTER", "filename": "poster.jpg", "totalSize": 20}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").exists())
                .andReturn().getResponse().getContentAsString();
        String uploadId = JsonPath.read(initResponse, "$.uploadId");

        mockMvc.perform(put("/api/admin/uploads/" + uploadId + "/chunks/0").with(asRole("ADMIN"))
                        .contentType("application/octet-stream")
                        .content(content))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/uploads/" + uploadId + "/complete").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(Matchers.startsWith("/posters/")))
                .andExpect(jsonPath("$.sizeBytes").value(20));
    }

    @Test
    @Order(4)
    void completingUnknownSessionReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/admin/uploads/" + UUID.randomUUID() + "/complete").with(asRole("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void cancelRemovesSession() throws Exception {
        String initResponse = mockMvc.perform(post("/api/admin/uploads").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"targetType": "MEDIA_DOCUMENT", "filename": "doc.pdf", "totalSize": 10}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String uploadId = JsonPath.read(initResponse, "$.uploadId");

        mockMvc.perform(delete("/api/admin/uploads/" + uploadId).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/uploads/" + uploadId + "/complete").with(asRole("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    void initOverSizeLimitIsRejectedBeforeAnyByteIsSent() throws Exception {
        // Limite documenti 10 MB: il rifiuto arriva all'avvio, non alla chiusura.
        mockMvc.perform(post("/api/admin/uploads").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"targetType": "MEDIA_DOCUMENT", "filename": "copione.pdf", "totalSize": 15728640}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.startsWith("File troppo grande")));
    }

    @Test
    @Order(7)
    void initWithWrongCategoryForTargetIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/uploads").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"targetType": "SHOW_POSTER", "filename": "copione.pdf", "totalSize": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("per questa destinazione")));
    }
}

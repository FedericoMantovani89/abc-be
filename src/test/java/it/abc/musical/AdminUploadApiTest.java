package it.abc.musical;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminUploadApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JavaMailSender mailSender;

    private static RequestPostProcessor asRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(j -> j.subject("test@abc.it"));
    }

    private static byte[] fakeJpegBytes(int size) {
        byte[] content = new byte[size];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return content;
    }

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
}

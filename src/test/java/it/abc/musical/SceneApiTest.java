package it.abc.musical;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scene e spettacoli via MockMvc: la risposta viene serializzata davvero da Jackson fuori dalla
 * transazione (open-in-view: false), quindi una collection lazy passata al DTO esplode qui.
 * Ogni richiesta e' una transazione nuova: le scene vengono ricaricate dal DB, con castRoles lazy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SceneApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JavaMailSender mailSender;

    static Long showId;
    static Long sceneId;

    private static RequestPostProcessor asRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(j -> j.subject("test@abc.it"));
    }

    private static Long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        showId = id(mockMvc.perform(post("/api/admin/shows").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Spettacolo scene",
                                 "cast": [{"firstName": "Anna", "lastName": "Rossi", "roleName": "Belle"}],
                                 "contentWarnings": []}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        sceneId = id(mockMvc.perform(post("/api/admin/shows/" + showId + "/scenes").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("{\"sceneNumber\": \"1\", \"title\": \"Apertura\", \"sortOrder\": 1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(put("/api/admin/shows/" + showId + "/scenes/" + sceneId + "/roles").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("{\"roles\": [\"Belle\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.castRoles[0]").value("Belle"));
    }

    @Test
    @Order(2)
    void listSerializesCastRoles() throws Exception {
        mockMvc.perform(get("/api/admin/shows/" + showId + "/scenes").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Apertura"))
                .andExpect(jsonPath("$[0].castRoles[0]").value("Belle"));
    }

    @Test
    @Order(3)
    void updateSerializesCastRoles() throws Exception {
        mockMvc.perform(put("/api/admin/shows/" + showId + "/scenes/" + sceneId).with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("{\"sceneNumber\": \"1\", \"title\": \"Apertura bis\", \"sortOrder\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Apertura bis"))
                .andExpect(jsonPath("$.castRoles[0]").value("Belle"));
    }

    @Test
    @Order(4)
    void showDetailAndListSerializeCollections() throws Exception {
        mockMvc.perform(get("/api/admin/shows/" + showId).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cast[0].roleName").value("Belle"))
                .andExpect(jsonPath("$.images.length()").value(0));
        mockMvc.perform(get("/api/public/shows/" + showId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cast[0].roleName").value("Belle"));
        mockMvc.perform(get("/api/admin/shows").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].castSize").value(1));
    }
}

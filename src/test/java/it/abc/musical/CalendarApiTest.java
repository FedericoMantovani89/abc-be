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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Calendario via MockMvc: la risposta viene serializzata davvero da Jackson fuori dalla
 * transazione (open-in-view: false), quindi una collection lazy non materializzata esplode qui.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CalendarApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JavaMailSender mailSender;

    static Long showId;
    static Long sceneId;
    static Long eventId;
    static Long usedTypeId;

    private static RequestPostProcessor asRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(j -> j.subject("test@abc.it"));
    }

    private static Long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private Long createType(String name) throws Exception {
        return id(mockMvc.perform(post("/api/admin/calendar-event-types").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\": \"" + name + "\", \"isRehearsalType\": true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @Order(1)
    void createRehearsalWithRolesAndScenes() throws Exception {
        showId = id(mockMvc.perform(post("/api/admin/shows").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("{\"title\": \"Spettacolo calendario\"}"))
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
                .andExpect(status().isOk());
        usedTypeId = createType("Prova Test");

        eventId = id(mockMvc.perform(post("/api/admin/calendar-events").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Prova scena 1", "eventTypeId": %d,
                                 "startDatetime": "2026-09-10T20:00:00", "endDatetime": "2026-09-10T23:00:00",
                                 "showId": %d, "sceneIds": [%d], "rehearsalRoles": ["Belle", "Bestia"]}
                                """.formatted(usedTypeId, showId, sceneId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rehearsalRoles.length()").value(2))
                .andExpect(jsonPath("$.scenes[0].castRoles[0]").value("Belle"))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @Order(2)
    void adminMonthSerializesLazyCollections() throws Exception {
        mockMvc.perform(get("/api/admin/calendar-events").param("year", "2026").param("month", "9")
                        .with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rehearsalRoles.length()").value(2))
                .andExpect(jsonPath("$[0].scenes[0].title").value("Apertura"))
                .andExpect(jsonPath("$[0].scenes[0].castRoles[0]").value("Belle"))
                .andExpect(jsonPath("$[0].eventType.active").value(true));
    }

    @Test
    @Order(3)
    void memberMonthAndDetailSerialize() throws Exception {
        mockMvc.perform(get("/api/member/calendar").param("year", "2026").param("month", "9")
                        .with(asRole("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rehearsalRoles.length()").value(2));

        mockMvc.perform(get("/api/admin/calendar-events/" + eventId).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes[0].castRoles[0]").value("Belle"));
    }

    @Test
    @Order(4)
    void updateSerializes() throws Exception {
        mockMvc.perform(put("/api/admin/calendar-events/" + eventId).with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Prova scena 1 bis", "eventTypeId": %d,
                                 "startDatetime": "2026-09-10T20:00:00", "endDatetime": "2026-09-10T20:00:00",
                                 "showId": %d, "sceneIds": [%d], "rehearsalRoles": ["Belle"]}
                                """.formatted(usedTypeId, showId, sceneId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Prova scena 1 bis"))
                .andExpect(jsonPath("$.rehearsalRoles.length()").value(1))
                .andExpect(jsonPath("$.scenes[0].castRoles[0]").value("Belle"));
    }

    @Test
    @Order(5)
    void endBeforeStartIsRejected() throws Exception {
        String body = """
                {"title": "Al contrario", "startDatetime": "2026-09-12T20:00:00",
                 "endDatetime": "2026-09-12T19:59:00"}
                """;
        mockMvc.perform(post("/api/admin/calendar-events").with(asRole("ADMIN"))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La data di fine precede la data di inizio"));
        mockMvc.perform(put("/api/admin/calendar-events/" + eventId).with(asRole("ADMIN"))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La data di fine precede la data di inizio"));
        // nessun evento in piu' creato, e quello esistente non e' stato toccato
        mockMvc.perform(get("/api/admin/calendar-events").param("year", "2026").param("month", "9")
                        .with(asRole("ADMIN")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Prova scena 1 bis"));
    }

    @Test
    @Order(6)
    void deleteUsedTypeDeactivatesIt() throws Exception {
        mockMvc.perform(delete("/api/admin/calendar-event-types/" + usedTypeId).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usedTypeId))
                .andExpect(jsonPath("$.outcome").value("DEACTIVATED"))
                .andExpect(jsonPath("$.type.active").value(false));

        mockMvc.perform(get("/api/admin/calendar-event-types").with(asRole("ADMIN")))
                .andExpect(jsonPath("$[?(@.id == " + usedTypeId + ")]").isEmpty());
        mockMvc.perform(get("/api/admin/calendar-event-types/all").with(asRole("ADMIN")))
                .andExpect(jsonPath("$[?(@.id == " + usedTypeId + ")].active").value(false));
        // l'evento continua a mostrare il suo tipo
        mockMvc.perform(get("/api/admin/calendar-events/" + eventId).with(asRole("ADMIN")))
                .andExpect(jsonPath("$.eventType.id").value(usedTypeId));
    }

    @Test
    @Order(7)
    void deleteTypeUsedOnlyBySoftDeletedEventDeactivatesIt() throws Exception {
        mockMvc.perform(delete("/api/admin/calendar-events/" + eventId).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/calendar-event-types/" + usedTypeId).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DEACTIVATED"));
    }

    @Test
    @Order(8)
    void deleteUnusedTypeRemovesIt() throws Exception {
        Long unused = createType("Mai usato");
        mockMvc.perform(delete("/api/admin/calendar-event-types/" + unused).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unused))
                .andExpect(jsonPath("$.outcome").value("DELETED"))
                .andExpect(jsonPath("$.type").doesNotExist());
        mockMvc.perform(get("/api/admin/calendar-event-types/all").with(asRole("ADMIN")))
                .andExpect(jsonPath("$[?(@.id == " + unused + ")]").isEmpty());
        mockMvc.perform(delete("/api/admin/calendar-event-types/" + unused).with(asRole("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    void memberCannotDeleteTypes() throws Exception {
        mockMvc.perform(delete("/api/admin/calendar-event-types/1").with(asRole("MEMBER")))
                .andExpect(status().isForbidden());
    }
}

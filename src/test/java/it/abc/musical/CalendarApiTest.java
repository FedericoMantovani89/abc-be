package it.abc.musical;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CalendarApiTest extends IntegrationTestBase {

    static Long showId;
    static Long eventId;
    static Long usedTypeId;

    private Long createType(String name) throws Exception {
        return id(mockMvc.perform(post("/api/admin/calendar-event-types").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\": \"" + name + "\", \"isRehearsalType\": true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @Order(1)
    void createRehearsalWithTwoCastRoles() throws Exception {
        showId = id(mockMvc.perform(post("/api/admin/shows").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Spettacolo calendario", "cast": [
                                  {"firstName": "Anna", "lastName": "Rossi", "roleName": "Belle"},
                                  {"firstName": "Luca", "lastName": "Bianchi", "roleName": "Bestia"},
                                  {"firstName": "Marco", "lastName": "Verdi", "roleName": "Gaston"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        usedTypeId = createType("Prova Test");

        eventId = id(mockMvc.perform(post("/api/admin/calendar-events").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Prova ruoli", "eventTypeId": %d,
                                 "startDatetime": "2026-09-10T20:00:00", "endDatetime": "2026-09-10T23:00:00",
                                 "showId": %d, "rehearsalRoles": ["Belle", "Bestia"]}
                                """.formatted(usedTypeId, showId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.showId").value(showId))
                .andExpect(jsonPath("$.rehearsalRoles.length()").value(2))
                .andExpect(jsonPath("$.scenes").doesNotExist())
                .andReturn().getResponse().getContentAsString());

        // riletto con GET: stessi 2 ruoli, nell'ordine inviato
        mockMvc.perform(get("/api/admin/calendar-events/" + eventId).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showTitle").value("Spettacolo calendario"))
                .andExpect(jsonPath("$.rehearsalRoles[0]").value("Belle"))
                .andExpect(jsonPath("$.rehearsalRoles[1]").value("Bestia"));
    }

    @Test
    @Order(2)
    void adminMonthSerializesLazyCollections() throws Exception {
        mockMvc.perform(get("/api/admin/calendar-events").param("year", "2026").param("month", "9")
                        .with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rehearsalRoles.length()").value(2))
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
                .andExpect(jsonPath("$.rehearsalRoles.length()").value(2));
    }

    @Test
    @Order(4)
    void updateSerializes() throws Exception {
        mockMvc.perform(put("/api/admin/calendar-events/" + eventId).with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Prova ruoli bis", "eventTypeId": %d,
                                 "startDatetime": "2026-09-10T20:00:00", "endDatetime": "2026-09-10T20:00:00",
                                 "showId": %d, "rehearsalRoles": ["belle"]}
                                """.formatted(usedTypeId, showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Prova ruoli bis"))
                .andExpect(jsonPath("$.rehearsalRoles.length()").value(1))
                // salvato con la grafia del cast
                .andExpect(jsonPath("$.rehearsalRoles[0]").value("Belle"));
    }

    @Test
    @Order(5)
    void roleNotInCastIsRejected() throws Exception {
        String body = """
                {"title": "Prova sbagliata", "eventTypeId": %d,
                 "startDatetime": "2026-09-11T20:00:00", "endDatetime": "2026-09-11T23:00:00",
                 "showId": %d, "rehearsalRoles": ["Belle", "Cenerentola"]}
                """.formatted(usedTypeId, showId);
        mockMvc.perform(post("/api/admin/calendar-events").with(asRole("ADMIN"))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Il ruolo \"Cenerentola\" non esiste nel cast dello spettacolo"));
        mockMvc.perform(put("/api/admin/calendar-events/" + eventId).with(asRole("ADMIN"))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        // l'evento esistente non e' stato toccato
        mockMvc.perform(get("/api/admin/calendar-events/" + eventId).with(asRole("ADMIN")))
                .andExpect(jsonPath("$.title").value("Prova ruoli bis"))
                .andExpect(jsonPath("$.rehearsalRoles.length()").value(1));
    }

    @Test
    @Order(6)
    void rolesWithoutShowAreRejected() throws Exception {
        mockMvc.perform(post("/api/admin/calendar-events").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Prova senza spettacolo", "eventTypeId": %d,
                                 "startDatetime": "2026-09-11T20:00:00", "rehearsalRoles": ["Belle"]}
                                """.formatted(usedTypeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("I ruoli della prova richiedono uno spettacolo"));
    }

    @Test
    @Order(7)
    void nonRehearsalTypeDropsShowAndRoles() throws Exception {
        Long meetingType = id(mockMvc.perform(post("/api/admin/calendar-event-types").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\": \"Riunione Test\", \"isRehearsalType\": false}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        Long meetingId = id(mockMvc.perform(post("/api/admin/calendar-events").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Riunione", "eventTypeId": %d, "startDatetime": "2026-10-01T20:00:00",
                                 "showId": %d, "rehearsalRoles": ["Inventato"]}
                                """.formatted(meetingType, showId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.showId").doesNotExist())
                .andExpect(jsonPath("$.rehearsalRoles.length()").value(0))
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(delete("/api/admin/calendar-events/" + meetingId).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(8)
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
                .andExpect(jsonPath("$[0].title").value("Prova ruoli bis"));
    }

    @Test
    @Order(9)
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
    @Order(10)
    void deleteTypeUsedOnlyBySoftDeletedEventDeactivatesIt() throws Exception {
        mockMvc.perform(delete("/api/admin/calendar-events/" + eventId).with(asRole("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/calendar-event-types/" + usedTypeId).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DEACTIVATED"));
    }

    @Test
    @Order(11)
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
    @Order(12)
    void memberCannotDeleteTypes() throws Exception {
        mockMvc.perform(delete("/api/admin/calendar-event-types/1").with(asRole("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    void targetRolesAreNormalizedAndRemovedFieldsAreGone() throws Exception {
        mockMvc.perform(post("/api/admin/calendar-events").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Riunione staff", "startDatetime": "2027-05-10T20:00:00",
                                 "targetRoles": " staff, ,member,STAFF", "isRecurring": true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetRoles").value("STAFF,MEMBER"))
                .andExpect(jsonPath("$.isRecurring").doesNotExist())
                .andExpect(jsonPath("$.recurrencePattern").doesNotExist())
                .andExpect(jsonPath("$.publicEventId").doesNotExist());

        mockMvc.perform(post("/api/admin/calendar-events").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "Per tutti", "startDatetime": "2027-05-11T20:00:00", "targetRoles": " , "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetRoles").doesNotExist());

        // Il socio MEMBER vede la riunione (MEMBER e' tra i ruoli), come chi ha "Per tutti".
        mockMvc.perform(get("/api/member/calendar").param("year", "2027").param("month", "5")
                        .with(asRole("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/member/calendar").param("year", "2027").param("month", "5")
                        .with(asRole("TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}

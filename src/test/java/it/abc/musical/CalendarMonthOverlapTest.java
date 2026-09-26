package it.abc.musical;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lettura del mese: devono uscire tutti gli eventi che si sovrappongono al mese
 * (marzo 2027), non solo quelli che ci iniziano. Ordine per data di inizio.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalendarMonthOverlapTest extends IntegrationTestBase {

    private void event(String title, String start, String end, String targetRoles) throws Exception {
        String endJson = end == null ? "null" : "\"" + end + "\"";
        String rolesJson = targetRoles == null ? "null" : "\"" + targetRoles + "\"";
        mockMvc.perform(post("/api/admin/calendar-events").with(asRole("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title": "%s", "startDatetime": "%s", "endDatetime": %s, "targetRoles": %s}
                                """.formatted(title, start, endJson, rolesJson)))
                .andExpect(status().isCreated());
    }

    @BeforeAll
    void seed() throws Exception {
        // dentro il mese
        event("dentro", "2027-03-10T10:00:00", "2027-03-10T12:00:00", null);
        // inizia il mese prima, finisce dentro
        event("da-febbraio", "2027-02-25T09:00:00", "2027-03-02T18:00:00", null);
        // inizia dentro, finisce il mese dopo
        event("fino-ad-aprile", "2027-03-28T09:00:00", "2027-04-03T18:00:00", null);
        // copre tutto il mese senza iniziare ne' finire dentro
        event("copre-tutto", "2027-02-20T09:00:00", "2027-04-10T18:00:00", null);
        // finisce esattamente all'inizio del mese: si tocca, quindi rientra
        event("bordo-inizio", "2027-02-28T20:00:00", "2027-03-01T00:00:00", null);
        // senza fine, dentro il mese
        event("istante", "2027-03-15T21:00:00", null, null);
        // riservato allo STAFF, copre tutto il mese
        event("solo-staff", "2027-02-01T09:00:00", "2027-05-01T18:00:00", "STAFF");

        // fuori: tutto a febbraio, tutto ad aprile, senza fine a febbraio, senza fine ad aprile
        event("febbraio", "2027-02-01T09:00:00", "2027-02-28T23:59:59", null);
        event("aprile", "2027-04-01T00:00:00", "2027-04-06T18:00:00", null);
        event("istante-febbraio", "2027-02-27T21:00:00", null, null);
        event("istante-aprile", "2027-04-01T00:00:00", null, null);
    }

    @Test
    void adminMonthReturnsEveryOverlappingEventByStart() throws Exception {
        mockMvc.perform(get("/api/admin/calendar-events").param("year", "2027").param("month", "3")
                        .with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains(
                        "solo-staff", "copre-tutto", "da-febbraio", "bordo-inizio",
                        "dentro", "istante", "fino-ad-aprile")));
    }

    @Test
    void adminNextMonthStillShowsEventsRunningIntoIt() throws Exception {
        mockMvc.perform(get("/api/admin/calendar-events").param("year", "2027").param("month", "4")
                        .with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains(
                        "solo-staff", "copre-tutto", "fino-ad-aprile", "aprile", "istante-aprile")));
    }

    @Test
    void memberMonthKeepsRoleFilter() throws Exception {
        mockMvc.perform(get("/api/member/calendar").param("year", "2027").param("month", "3")
                        .with(asRole("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains(
                        "copre-tutto", "da-febbraio", "bordo-inizio",
                        "dentro", "istante", "fino-ad-aprile")));

        mockMvc.perform(get("/api/member/calendar").param("year", "2027").param("month", "3")
                        .with(asRole("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains(
                        "solo-staff", "copre-tutto", "da-febbraio", "bordo-inizio",
                        "dentro", "istante", "fino-ad-aprile")));
    }
}

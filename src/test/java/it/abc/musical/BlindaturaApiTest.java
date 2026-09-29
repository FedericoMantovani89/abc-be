package it.abc.musical;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Il backend risponde solo al sito (app.frontend-url del profilo test: http://localhost:3000) e
 * da fuori mostra solo la salute, senza dettagli.
 */
class BlindaturaApiTest extends IntegrationTestBase {

    private static final String FOREIGN_SITE = "https://sito-estraneo.it";
    private static final String REAL_SITE = "http://localhost:3000";

    @Test
    void requestFromAForeignSiteIsRejected() throws Exception {
        mockMvc.perform(get("/api/public/shows").header(HttpHeaders.ORIGIN, FOREIGN_SITE))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightFromAForeignSiteIsRejected() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, FOREIGN_SITE)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void requestFromTheRealSiteIsAccepted() throws Exception {
        mockMvc.perform(get("/api/public/shows").header(HttpHeaders.ORIGIN, REAL_SITE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, REAL_SITE))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflightFromTheRealSiteIsAccepted() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, REAL_SITE)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, REAL_SITE));
    }

    @Test
    void localhostIsNotAllowedUnlessTheSiteItselfIsLocalhost() throws Exception {
        // Nel profilo test il sito e' localhost:3000, quindi un'altra porta di localhost non passa.
        mockMvc.perform(get("/api/public/shows").header(HttpHeaders.ORIGIN, "http://localhost:4000"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthShowsOnlyTheStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void healthEvenForAdminsHasNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotExposedNotEvenToAdmins() throws Exception {
        for (String endpoint : new String[] {"info", "env", "beans", "metrics", "mappings", "configprops"}) {
            mockMvc.perform(get("/actuator/" + endpoint).with(asRole("ADMIN")))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void otherActuatorEndpointsRequireLoginFromOutside() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }
}

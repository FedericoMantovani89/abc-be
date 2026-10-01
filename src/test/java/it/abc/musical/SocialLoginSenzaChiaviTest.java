package it.abc.musical;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Il sito deve partire con le chiavi Google/Facebook vuote (come nel .env del pacchetto). */
@TestPropertySource(properties = {
        "app.social.google.client-id=", "app.social.google.client-secret=",
        "app.social.facebook.client-id=", "app.social.facebook.client-secret="})
class SocialLoginSenzaChiaviTest extends IntegrationTestBase {

    @Test
    void ilContestoPartePerPrimoEDichiaraNessunProviderAttivo() throws Exception {
        mockMvc.perform(get("/api/public/auth/social"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(false))
                .andExpect(jsonPath("$.facebook").value(false));
    }

    @Test
    void ilPulsanteDiUnProviderSpentoDaUnErroreChiaroNonUn500() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login?error=social_non_attivo")));
    }
}

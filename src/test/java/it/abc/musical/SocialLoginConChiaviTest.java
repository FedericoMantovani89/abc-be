package it.abc.musical;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con le chiavi (profilo test) entrambi i provider sono attivi e portano al provider vero. */
class SocialLoginConChiaviTest extends IntegrationTestBase {

    @Test
    void entrambiAttiviERedirectVersoIlProvider() throws Exception {
        mockMvc.perform(get("/api/public/auth/social"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(true))
                .andExpect(jsonPath("$.facebook").value(true));
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("accounts.google.com")));
        mockMvc.perform(get("/oauth2/authorization/facebook"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("facebook.com")));
    }
}

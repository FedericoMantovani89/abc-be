package it.abc.musical;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un guasto della posta (Aruba) non deve rendere il backend "malato" per l'healthcheck del compose. */
@Import(SaluteSenzaPostaTest.PostaGuasta.class)
class SaluteSenzaPostaTest extends IntegrationTestBase {

    @TestConfiguration
    static class PostaGuasta {
        @Bean
        HealthIndicator mailHealthIndicator() {
            return () -> Health.down().build();
        }
    }

    @Test
    void ilGruppoSitoRestaUpConLaPostaGiu() throws Exception {
        mockMvc.perform(get("/actuator/health/sito"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void lAggregatoCompletoEIlGruppoPostaMostranoIlGuasto() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/actuator/health/posta"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
}

package it.abc.musical;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ogni risposta di errore ha "error" (il messaggio di sempre) e "code" (il codice stabile
 * che il frontend legge). Nessun campo tolto rispetto a prima.
 */
class CodiciErroreApiTest extends IntegrationTestBase {

    @Test
    void notFoundHasMessageAndCode() throws Exception {
        mockMvc.perform(get("/api/admin/shows/999999").with(asRole("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Spettacolo non trovato"))
                .andExpect(jsonPath("$.code").value("spettacolo.non.trovato"));
    }

    @Test
    void badRequestHasMessageAndCode() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"token\": \"non-esiste\", \"newPassword\": \"Abcdef12345!x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Token non valido"))
                .andExpect(jsonPath("$.code").value("auth.token.non.valido"));
    }

    @Test
    void messageWithValuesKeepsTheOldTextAndTheStableCode() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("role", "NON_ESISTE").with(asRole("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ruolo non valido: NON_ESISTE"))
                .andExpect(jsonPath("$.code").value("ruolo.non.valido"));
    }

    @Test
    void validationKeepsFieldsAndAddsTheCode() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dati non validi"))
                .andExpect(jsonPath("$.code").value("errore.dati.non.validi"))
                .andExpect(jsonPath("$.fields").isMap());
    }

    @Test
    void unreadableBodyHasCode() throws Exception {
        mockMvc.perform(post("/api/admin/calendar-event-types").with(asRole("ADMIN"))
                        .contentType("application/json").content("{non json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Corpo della richiesta non leggibile"))
                .andExpect(jsonPath("$.code").value("errore.corpo.non.leggibile"));
    }

    @Test
    void parameterErrorsCarryTheParameterNameInTheMessage() throws Exception {
        mockMvc.perform(get("/api/auth/verify"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parametro 'token' obbligatorio"))
                .andExpect(jsonPath("$.code").value("errore.parametro.obbligatorio"));
        mockMvc.perform(get("/api/admin/shows/non-numerico").with(asRole("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parametro 'id' non valido"))
                .andExpect(jsonPath("$.code").value("errore.parametro.non.valido"));
    }

    @Test
    void missingResourceHasCode() throws Exception {
        mockMvc.perform(get("/posters/questo-file-non-esiste-mai.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("errore.risorsa.non.trovata"));
    }

    @Test
    void failedLoginKeepsTheOldFieldsAndAddsTheCode() throws Exception {
        mockMvc.perform(post("/api/auth/login").param("username", "nessuno@example.com").param("password", "sbagliata"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.code").value("auth.credenziali.non.valide"));
    }

    @Test
    void uploadSessionErrorHasCode() throws Exception {
        mockMvc.perform(put("/api/admin/uploads/non-esiste/chunks/0").with(asRole("ADMIN"))
                        .contentType("application/octet-stream").content(new byte[] {1}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", hasKey("code")));
    }
}

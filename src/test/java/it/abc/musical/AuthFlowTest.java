package it.abc.musical;

import it.abc.musical.entities.Token;
import it.abc.musical.entities.User;
import it.abc.musical.repositories.TokenRepository;
import it.abc.musical.repositories.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flusso completo: registrazione → verifica email → form login → scambio sessione/JWT.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowTest extends IntegrationTestBase {

    @Autowired
    UserRepository userRepository;

    @Autowired
    TokenRepository tokenRepository;

    @Autowired
    JwtDecoder jwtDecoder;

    private static final String EMAIL = "mario.rossi@example.com";
    private static final String PASSWORD = "Password1!";

    @Test
    @Order(1)
    void registerCreatesUnverifiedUserWithVerificationToken() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email": "%s", "password": "%s",
                                 "firstName": "Mario", "lastName": "Rossi"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        User user = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(user.isVerified()).isFalse();
        assertThat(user.getRole().getName()).isEqualTo("REGISTER");
    }

    @Test
    @Order(2)
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email": "x@example.com", "password": "debole",
                                 "firstName": "X", "lastName": "Y"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    void loginBeforeVerificationIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/x-www-form-urlencoded")
                        .content("username=" + EMAIL + "&password=" + PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void verifyActivatesAccountAndRedirectsToFrontend() throws Exception {
        User user = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        Token token = tokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId())
                        && Token.TYPE_EMAIL_VERIFICATION.equals(t.getTokenType()))
                .findFirst().orElseThrow();

        mockMvc.perform(get("/api/auth/verify").param("token", token.getTokenValue()))
                .andExpect(status().is3xxRedirection());

        assertThat(userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow().isVerified()).isTrue();
    }

    @Test
    @Order(5)
    void loginThenTokenExchangeYieldsJwt() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/x-www-form-urlencoded")
                        .content("username=" + EMAIL + "&password=" + PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String tokenResponse = mockMvc.perform(post("/api/auth/token")
                        .session((org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();

        // Login con password: Spring Security aggiunge alle autorita' anche il marcatore
        // FACTOR_PASSWORD (metodo di login usato), non un ruolo. AuthUtil.roles deve tenere
        // solo le autorita' ROLE_*, altrimenti finisce nel claim roles del JWT come se fosse
        // un ruolo vero (visto in sidebar admin dal frontend).
        String accessToken = com.jayway.jsonpath.JsonPath.read(tokenResponse, "$.accessToken");
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("REGISTER");
    }

    @Test
    @Order(6)
    void tokenWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/token"))
                .andExpect(status().isUnauthorized());
    }
}

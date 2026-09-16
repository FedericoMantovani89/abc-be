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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TokenRepository tokenRepository;

    @MockitoBean
    JavaMailSender mailSender;

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

        mockMvc.perform(post("/api/auth/token")
                        .session((org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @Order(6)
    void tokenWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/token"))
                .andExpect(status().isUnauthorized());
    }
}

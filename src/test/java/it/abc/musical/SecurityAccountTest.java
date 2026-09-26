package it.abc.musical;

import it.abc.musical.entities.AuditLog;
import it.abc.musical.entities.Token;
import it.abc.musical.entities.User;
import it.abc.musical.repositories.AuditLogRepository;
import it.abc.musical.repositories.RoleRepository;
import it.abc.musical.repositories.TokenRepository;
import it.abc.musical.repositories.UserRepository;
import it.abc.musical.security.JwtTokenService;
import it.abc.musical.security.OAuth2UserProvisioningService;
import it.abc.musical.services.TokenService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sicurezza e account (audit 26/09): revoca dell'accesso con JWT gia' emessi, limite dei
 * login per IP del visitatore, login falliti nel registro, recupero password, gestione
 * utenti, proprio account, aggancio Google/Facebook di un account mai verificato.
 * Le chiamate a /api/auth/** portano ognuna un IP diverso in X-Forwarded-For, cosi' il
 * limite di 20 richieste/ora non mescola i test tra loro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecurityAccountTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String PASSWORD = "Password1!";
    private static final String NEW_PASSWORD = "NuovaPass2!";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    TokenRepository tokenRepository;
    @Autowired
    AuditLogRepository auditLogRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtTokenService jwtTokenService;
    @Autowired
    TokenService tokenService;
    @Autowired
    OAuth2UserProvisioningService provisioningService;

    @MockitoBean
    JavaMailSender mailSender;

    @BeforeEach
    void mail() {
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
    }

    private User createUser(String roleName, boolean verified) {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@example.com");
        u.setPassword(passwordEncoder.encode(PASSWORD));
        u.setFirstName("Mario");
        u.setLastName("Rossi");
        u.setActive(true);
        u.setVerified(verified);
        u.setRole(roleRepository.findByName(roleName).orElseThrow());
        return userRepository.save(u);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenService.generateTokenForUser(user, 3600);
    }

    private static MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder request, String ip) {
        return request.header("X-Forwarded-For", ip);
    }

    private static RequestPostProcessor asAdmin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")).jwt(j -> j.subject("admin@abc.it"));
    }

    // --- Revoca dell'accesso (sicurezza #1) -------------------------------------------------

    @Test
    void deactivatedUserTokenIsRejected() throws Exception {
        User member = createUser("MEMBER", true);
        String token = bearer(member);
        mockMvc.perform(get("/api/member/communications").header("Authorization", token))
                .andExpect(status().isOk());

        member.setActive(false);
        userRepository.save(member);

        mockMvc.perform(get("/api/member/communications").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangeAppliesToTokensAlreadyIssued() throws Exception {
        User user = createUser("MEMBER", true);
        String token = bearer(user); // nel token: roles = [MEMBER]
        mockMvc.perform(get("/api/admin/shows").header("Authorization", token))
                .andExpect(status().isForbidden());

        user.setRole(roleRepository.findByName("STAFF").orElseThrow());
        userRepository.save(user);
        mockMvc.perform(get("/api/admin/shows").header("Authorization", token))
                .andExpect(status().isOk());

        user.setRole(roleRepository.findByName("REGISTER").orElseThrow());
        userRepository.save(user);
        mockMvc.perform(get("/api/member/communications").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    // --- Limite dei login per IP del visitatore (sicurezza #3) ------------------------------

    @Test
    void rateLimitCountsEachVisitorIpSeparately() throws Exception {
        String body = """
                {"email": "nessuno@example.com"}
                """;
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(fromIp(post("/api/auth/forgot-password"), "203.0.113.10, 10.0.0.1")
                            .contentType("application/json").content(body))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(fromIp(post("/api/auth/forgot-password"), "203.0.113.10, 10.0.0.2")
                        .contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests());

        // Un altro visitatore, dietro lo stesso server Next, non e' bloccato.
        mockMvc.perform(fromIp(post("/api/auth/forgot-password"), "203.0.113.20, 10.0.0.1")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    @Test
    void blankForwardedForFallsBackToConnectionIp() throws Exception {
        String body = """
                {"email": "nessuno@example.com"}
                """;
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(fromIp(post("/api/auth/forgot-password"), " ")
                            .contentType("application/json").content(body))
                    .andExpect(status().isOk());
        }
        // Senza intestazione conta l'IP della connessione: lo stesso secchio di prima.
        mockMvc.perform(post("/api/auth/forgot-password").contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests());
    }

    // --- Login falliti nel registro attivita' ------------------------------------------------

    @Test
    void failedLoginIsRecordedWithAccountAndIp() throws Exception {
        User user = createUser("MEMBER", true);

        mockMvc.perform(fromIp(post("/api/auth/login"), "198.51.100.7")
                        .contentType("application/x-www-form-urlencoded")
                        .content("username=" + user.getEmail() + "&password=Sbagliata1!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));

        List<AuditLog> rows = auditLogRepository.findAll().stream()
                .filter(a -> "LOGIN_FAILED".equals(a.getAction()) && "198.51.100.7".equals(a.getIpAddress()))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEntityId()).isEqualTo(user.getId());
        assertThat(rows.get(0).getUserId()).isNull();
    }

    // --- Recupero password (qualita', buco 2) ----------------------------------------------

    @Test
    void passwordResetWithValidTokenThenReuseIsRejected() throws Exception {
        User user = createUser("MEMBER", true);
        mockMvc.perform(fromIp(post("/api/auth/forgot-password"), "198.51.100.20")
                        .contentType("application/json")
                        .content("{\"email\": \"" + user.getEmail().toUpperCase() + "\"}"))
                .andExpect(status().isOk());
        Token token = tokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId())
                        && Token.TYPE_PASSWORD_RESET.equals(t.getTokenType()))
                .findFirst().orElseThrow();

        String resetBody = """
                {"token": "%s", "newPassword": "%s"}
                """.formatted(token.getTokenValue(), NEW_PASSWORD);
        mockMvc.perform(fromIp(post("/api/auth/reset-password"), "198.51.100.20")
                        .contentType("application/json").content(resetBody))
                .andExpect(status().isOk());
        assertThat(passwordEncoder.matches(NEW_PASSWORD,
                userRepository.findById(user.getId()).orElseThrow().getPassword())).isTrue();

        mockMvc.perform(fromIp(post("/api/auth/reset-password"), "198.51.100.20")
                        .contentType("application/json").content(resetBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Token già utilizzato"));
    }

    @Test
    void passwordResetWithExpiredTokenIsRejected() throws Exception {
        User user = createUser("MEMBER", true);
        Token token = tokenService.createToken(user, Token.TYPE_PASSWORD_RESET);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(token);

        mockMvc.perform(fromIp(post("/api/auth/reset-password"), "198.51.100.21")
                        .contentType("application/json")
                        .content("""
                                {"token": "%s", "newPassword": "%s"}
                                """.formatted(token.getTokenValue(), NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Token scaduto"));
        assertThat(passwordEncoder.matches(PASSWORD,
                userRepository.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    // --- Gestione utenti (qualita', buco 3) ------------------------------------------------

    @Test
    void godRoleIsNotAssignable() throws Exception {
        User user = createUser("MEMBER", true);
        mockMvc.perform(put("/api/admin/users/" + user.getId() + "/role").with(asAdmin())
                        .contentType("application/json").content("{\"roleName\": \"god\"}"))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getRole().getName()).isEqualTo("MEMBER");
    }

    @Test
    void technicalAccountIsNotModifiable() throws Exception {
        User god = createUser("GOD", true);
        mockMvc.perform(put("/api/admin/users/" + god.getId() + "/status").with(asAdmin())
                        .contentType("application/json").content("{\"active\": false}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/users/" + god.getId() + "/role").with(asAdmin())
                        .contentType("application/json").content("{\"roleName\": \"MEMBER\"}"))
                .andExpect(status().isConflict());
        User reloaded = userRepository.findById(god.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getRole().getName()).isEqualTo("GOD");
    }

    @Test
    void adminDeactivationCutsOffTheUser() throws Exception {
        User member = createUser("MEMBER", true);
        String token = bearer(member);

        mockMvc.perform(put("/api/admin/users/" + member.getId() + "/status").with(asAdmin())
                        .contentType("application/json").content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/account/me").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    // --- Proprio account (qualita', buco 4) ------------------------------------------------

    @Test
    void changePasswordRequiresTheCurrentOne() throws Exception {
        User user = createUser("MEMBER", true);
        String token = bearer(user);

        mockMvc.perform(put("/api/account/password").header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"currentPassword\": \"Sbagliata1!\", \"newPassword\": \"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password attuale non corretta"));

        mockMvc.perform(put("/api/account/password").header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(passwordEncoder.matches(NEW_PASSWORD,
                userRepository.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void deleteAccountRequiresPasswordAnonymizesAndRevokesAccess() throws Exception {
        User user = createUser("MEMBER", true);
        String token = bearer(user);

        mockMvc.perform(delete("/api/account").header("Authorization", token)
                        .contentType("application/json").content("{\"currentPassword\": \"Sbagliata1!\"}"))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getDeletedAt()).isNull();

        mockMvc.perform(delete("/api/account").header("Authorization", token)
                        .contentType("application/json").content("{\"currentPassword\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        User deleted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.isActive()).isFalse();
        assertThat(deleted.getEmail()).isEqualTo("deleted+" + user.getId() + "@deleted.invalid");
        assertThat(deleted.getPassword()).isNull();
        assertThat(deleted.getFirstName()).isNull();

        mockMvc.perform(get("/api/account/me").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    // --- Aggancio Google/Facebook (sicurezza #6) -------------------------------------------

    @Test
    void oauthOnUnverifiedAccountDropsThePasswordChosenByWhoeverRegistered() {
        User squatted = createUser("REGISTER", false);

        provisioningService.provision("google", "sub-1", squatted.getEmail(), "Mario", "Rossi");

        User reloaded = userRepository.findById(squatted.getId()).orElseThrow();
        assertThat(reloaded.isVerified()).isTrue();
        assertThat(reloaded.getPassword()).isNull();
        assertThat(reloaded.getOauthProvider()).isEqualTo("google");
    }

    @Test
    void oauthOnVerifiedAccountKeepsItsPassword() {
        User user = createUser("MEMBER", true);

        provisioningService.provision("google", "sub-2", user.getEmail(), "Mario", "Rossi");

        assertThat(passwordEncoder.matches(PASSWORD,
                userRepository.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    // --- Limiti dei campi di registrazione (sicurezza #4) ----------------------------------

    @Test
    void registrationRejectsNamesLongerThanTheColumn() throws Exception {
        mockMvc.perform(fromIp(post("/api/auth/register"), "198.51.100.30")
                        .contentType("application/json")
                        .content("""
                                {"email": "lungo@example.com", "password": "%s",
                                 "firstName": "%s", "lastName": "Rossi"}
                                """.formatted(PASSWORD, "a".repeat(101))))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.existsByEmailIgnoreCase("lungo@example.com")).isFalse();
    }
}

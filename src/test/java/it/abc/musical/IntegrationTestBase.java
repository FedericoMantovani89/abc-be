package it.abc.musical;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Base dei test di integrazione che avviano il context Spring contro un Postgres vero.
 * Un solo container per l'intera suite (pattern "singleton container" di Testcontainers:
 * campo static avviato una volta, ripulito da Ryuk a fine JVM) invece di uno per classe,
 * e stesse annotazioni per tutte le sottoclassi cosi' Spring riusa anche un solo context.
 * <p>
 * Restano fuori {@code SecurityAccountTest} (i suoi test sul limite di richieste per IP
 * condividerebbero i bucket di {@code RateLimitingFilter} con le richieste di altre classi
 * verso {@code /api/auth/**}, rendendo i conteggi dipendenti dall'ordine di esecuzione) e
 * {@code HeroFocusMobileMigrationTest} (controlla Flyway a mano fino a una versione
 * intermedia: ha bisogno di un proprio schema vergine, non di uno gia' all'ultima versione).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected JavaMailSender mailSender;

    protected static RequestPostProcessor asRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(j -> j.subject("test@abc.it"));
    }

    protected static Long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}

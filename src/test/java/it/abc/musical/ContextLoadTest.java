package it.abc.musical;

import it.abc.musical.repositories.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avvia l'intero application context contro un Postgres reale:
 * verifica che Flyway applichi V001 e che il mapping JPA sia valido (ddl-auto=validate).
 */
class ContextLoadTest extends IntegrationTestBase {

    @Autowired
    RoleRepository roleRepository;

    @Test
    void contextLoadsAndSeedRolesPresent() {
        assertThat(roleRepository.findByName("ADMIN")).isPresent();
        // 7 ruoli da V001 + REGISTER (V002): utente auto-registrato, poco più di PUBLIC,
        // non vede i contenuti riservati ai soci finché un admin non lo promuove a MEMBER.
        assertThat(roleRepository.count()).isEqualTo(8);
    }
}

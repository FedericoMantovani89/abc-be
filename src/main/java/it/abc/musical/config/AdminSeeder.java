package it.abc.musical.config;

import it.abc.musical.entities.Role;
import it.abc.musical.entities.User;
import it.abc.musical.repositories.RoleRepository;
import it.abc.musical.repositories.UserRepository;
import it.abc.musical.security.Roles;
import it.abc.musical.util.EmailAddresses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea l'utente ADMIN al primo avvio leggendo ADMIN_EMAIL / ADMIN_PASSWORD.
 * Idempotente: se l'email esiste già non fa nulla.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD non impostati: nessun admin creato");
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            return;
        }
        Role adminRole = roleRepository.findByName(Roles.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Ruolo ADMIN mancante"));

        User admin = new User();
        admin.setEmail(EmailAddresses.normalize(adminEmail));
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setFirstName("Admin");
        admin.setLastName("ABC");
        admin.setActive(true);
        admin.setVerified(true);
        admin.setRole(adminRole);
        userRepository.save(admin);
        log.info("Utente admin creato: {}", adminEmail);
    }
}

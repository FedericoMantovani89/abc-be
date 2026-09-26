package it.abc.musical.security;

import it.abc.musical.entities.User;
import it.abc.musical.repositories.UserRepository;
import it.abc.musical.util.EmailAddresses;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Crea o aggiorna l'utente locale a partire dai dati del provider OAuth2.
 * Gli utenti OAuth nascono verificati (l'email è garantita dal provider) con ruolo REGISTER.
 */
@Service
@RequiredArgsConstructor
public class OAuth2UserProvisioningService {

    private final UserRepository userRepository;
    private final NewUserRole newUserRole;

    @Transactional
    public User provision(String provider, String oauthId, String email,
                          String firstName, String lastName) {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Il provider " + provider + " non ha fornito l'email");
        }
        String normalized = EmailAddresses.normalize(email);
        User user = userRepository.findByEmailIgnoreCase(normalized).orElseGet(() -> {
            // Nuovo utente OAuth: nasce come REGISTER (in attesa di approvazione).
            // Gli utenti già esistenti mantengono il ruolo che hanno.
            User u = new User();
            u.setEmail(normalized);
            u.setRole(newUserRole.get());
            return u;
        });
        if (!user.isVerified()) {
            // Account creato col form e mai verificato: la password l'ha scelta chi l'ha
            // registrato, non per forza il titolare dell'email, che il provider ha appena
            // confermato. Si azzera: il titolare ne imposta una sua dall'area account.
            user.setPassword(null);
        }
        user.setOauthProvider(provider);
        user.setOauthId(oauthId);
        if (firstName != null && !firstName.isBlank()) {
            user.setFirstName(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            user.setLastName(lastName);
        }
        user.setVerified(true);
        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}

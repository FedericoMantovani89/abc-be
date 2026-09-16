package it.abc.musical.security;

import it.abc.musical.entities.Role;
import it.abc.musical.entities.User;
import it.abc.musical.repositories.RoleRepository;
import it.abc.musical.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Crea o aggiorna l'utente locale a partire dai dati del provider OAuth2.
 * Gli utenti OAuth nascono verificati (l'email è garantita dal provider) con ruolo MEMBER.
 */
@Service
@RequiredArgsConstructor
public class OAuth2UserProvisioningService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public User provision(String provider, String oauthId, String email,
                          String firstName, String lastName, String pictureUrl) {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Il provider " + provider + " non ha fornito l'email");
        }
        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            // Nuovo utente OAuth: nasce come REGISTER (in attesa di approvazione).
            // Gli utenti già esistenti mantengono il ruolo che hanno.
            Role registerRole = roleRepository.findByName("REGISTER")
                    .orElseThrow(() -> new IllegalStateException("Ruolo REGISTER mancante"));
            User u = new User();
            u.setEmail(email.toLowerCase());
            u.setRole(registerRole);
            return u;
        });
        user.setOauthProvider(provider);
        user.setOauthId(oauthId);
        if (firstName != null && !firstName.isBlank()) {
            user.setFirstName(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            user.setLastName(lastName);
        }
        if (pictureUrl != null && !pictureUrl.isBlank()) {
            user.setProfilePictureUrl(pictureUrl);
        }
        user.setVerified(true);
        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}

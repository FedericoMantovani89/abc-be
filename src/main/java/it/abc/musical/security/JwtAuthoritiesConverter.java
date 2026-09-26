package it.abc.musical.security;

import it.abc.musical.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Ricava i ruoli di una richiesta con JWT dal database, non dal token: cosi' disattivare,
 * cancellare o cambiare ruolo a un utente vale subito, anche per i token gia' emessi
 * (con "ricordami" durano 30 giorni). Utente sconosciuto, disattivato o cancellato = 401.
 * Il claim "roles" resta nel token solo per il frontend, che lo legge per la sessione.
 * Una query per richiesta: a questa scala non si nota.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final UserRepository userRepository;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (!(jwt.getClaim("userId") instanceof Number userId)) {
            throw new InvalidBearerTokenException("Token senza utente");
        }
        return userRepository.findById(userId.longValue())
                .filter(u -> u.isActive() && u.getDeletedAt() == null)
                .<Collection<GrantedAuthority>>map(u ->
                        List.of(new SimpleGrantedAuthority(Roles.authority(u.getRole().getName()))))
                .orElseThrow(() -> new InvalidBearerTokenException("Account non attivo"));
    }
}

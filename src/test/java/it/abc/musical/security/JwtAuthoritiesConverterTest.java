package it.abc.musical.security;

import it.abc.musical.entities.Role;
import it.abc.musical.entities.User;
import it.abc.musical.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthoritiesConverterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthoritiesConverter converter = new JwtAuthoritiesConverter(userRepository);

    private Jwt jwt(Long userId, List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("test@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("roles", roles);
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.build();
    }

    private User user(String roleName, boolean active, LocalDateTime deletedAt) {
        Role role = new Role();
        role.setName(roleName);
        User u = new User();
        u.setId(7L);
        u.setRole(role);
        u.setActive(active);
        u.setDeletedAt(deletedAt);
        return u;
    }

    @Test
    void roleComesFromDatabaseNotFromToken() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user("STAFF", true, null)));

        Collection<GrantedAuthority> authorities = converter.convert(jwt(7L, List.of("ADMIN", "MEMBER")));

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STAFF");
    }

    @Test
    void deactivatedUserIsRejected() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user("MEMBER", false, null)));

        assertThatThrownBy(() -> converter.convert(jwt(7L, List.of("MEMBER"))))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void deletedUserIsRejected() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user("MEMBER", true, LocalDateTime.now())));

        assertThatThrownBy(() -> converter.convert(jwt(7L, List.of("MEMBER"))))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(jwt(7L, List.of("MEMBER"))))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void tokenWithoutUserIdIsRejected() {
        assertThatThrownBy(() -> converter.convert(jwt(null, List.of("ADMIN"))))
                .isInstanceOf(InvalidBearerTokenException.class);
    }
}

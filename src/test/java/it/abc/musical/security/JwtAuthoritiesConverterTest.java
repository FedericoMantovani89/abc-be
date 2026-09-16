package it.abc.musical.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthoritiesConverterTest {

    private final JwtAuthoritiesConverter converter = new JwtAuthoritiesConverter();

    private Jwt jwtWithRoles(Object rolesClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("test@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (rolesClaim != null) {
            builder.claim("roles", rolesClaim);
        }
        return builder.build();
    }

    @Test
    void addsRolePrefixToEachRole() {
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithRoles(List.of("ADMIN", "MEMBER")));

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_MEMBER");
    }

    @Test
    void missingRolesClaimYieldsNoAuthorities() {
        assertThat(converter.convert(jwtWithRoles(null))).isEmpty();
    }
}

package it.abc.musical.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import it.abc.musical.entities.Role;
import it.abc.musical.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-32-bytes-long-for-hs256!";

    private final SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtTokenService service =
            new JwtTokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), "abc-musical");
    private final JwtDecoder decoder =
            NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

    private User testUser() {
        Role role = new Role();
        role.setName("MEMBER");
        User user = new User();
        user.setId(42L);
        user.setEmail("mario@example.com");
        user.setFirstName("Mario");
        user.setRole(role);
        return user;
    }

    @Test
    void tokenFromAuthenticationCarriesAllClaims() {
        CustomUserDetails details = new CustomUserDetails(testUser());
        Authentication auth = new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());

        Jwt jwt = decoder.decode(service.generateToken(auth, 3600));

        assertThat(jwt.getSubject()).isEqualTo("mario@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("MEMBER");
        assertThat((Object) jwt.getClaim("userId")).isEqualTo(42L);
        assertThat(jwt.getClaimAsString("firstName")).isEqualTo("Mario");
        assertThat(jwt.getAudience()).containsExactly(JwtTokenService.AUDIENCE);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("abc-musical");
    }

    @Test
    void tokenForUserMatchesAuthenticationFormat() {
        Jwt jwt = decoder.decode(service.generateTokenForUser(testUser(), 3600));

        assertThat(jwt.getSubject()).isEqualTo("mario@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("MEMBER");
        assertThat((Object) jwt.getClaim("userId")).isEqualTo(42L);
    }
}

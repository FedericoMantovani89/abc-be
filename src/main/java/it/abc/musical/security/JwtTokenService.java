package it.abc.musical.security;

import it.abc.musical.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JwtTokenService {

    public static final String AUDIENCE = "abc-musical-api";

    private final JwtEncoder jwtEncoder;
    private final String issuer;

    public JwtTokenService(JwtEncoder jwtEncoder, @Value("${jwt.issuer}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
    }

    public String generateToken(Authentication authentication, long expirationSeconds) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(Collectors.toSet());
        Long userId = null;
        String firstName = null;
        if (authentication.getPrincipal() instanceof CustomUserDetails ud) {
            userId = ud.getId();
            firstName = ud.getUser().getFirstName();
        }
        return buildToken(authentication.getName(), roles, userId, firstName, expirationSeconds);
    }

    /** Usato dal flusso OAuth2, dove il principal non è un CustomUserDetails. */
    public String generateTokenForUser(User user, long expirationSeconds) {
        return buildToken(user.getEmail(), Set.of(user.getRole().getName()),
                user.getId(), user.getFirstName(), expirationSeconds);
    }

    private String buildToken(String subject, Set<String> roles, Long userId,
                              String firstName, long expirationSeconds) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .subject(subject)
                .claim("roles", roles)
                .claim("userId", userId)
                .claim("firstName", firstName)
                .audience(List.of(AUDIENCE))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims
        )).getTokenValue();
    }
}

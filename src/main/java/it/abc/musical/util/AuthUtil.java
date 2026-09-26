package it.abc.musical.util;

import it.abc.musical.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Estrae identità e ruoli dall'Authentication (JWT bearer o sessione). */
public final class AuthUtil {

    private AuthUtil() {
    }

    public static Set<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(Collectors.toSet());
    }

    public static Long userId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            Object userId = jwt.getClaim("userId");
            if (userId instanceof Number n) {
                return n.longValue();
            }
            return null;
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details.getId();
        }
        return null;
    }

    /** Ruoli che vedono comunque tutto (calendario, comunicazioni, archivio). */
    private static final Set<String> SEE_ALL_ROLES = Set.of("ADMIN", "GOD");

    /** True se l'utente ha almeno uno dei ruoli nella stringa comma-separated (null/blank = tutti). */
    public static boolean matchesTargetRoles(String targetRoles, Set<String> userRoles) {
        return matchesRoles(RoleCsv.parse(targetRoles), userRoles);
    }

    /** True se l'utente ha almeno uno dei ruoli ammessi; nessun ruolo ammesso = tutti. */
    public static boolean matchesRoles(Collection<String> allowedRoles, Set<String> userRoles) {
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }
        if (userRoles.stream().anyMatch(SEE_ALL_ROLES::contains)) {
            return true;
        }
        return allowedRoles.stream().anyMatch(userRoles::contains);
    }
}

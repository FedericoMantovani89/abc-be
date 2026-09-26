package it.abc.musical.util;

import it.abc.musical.security.CustomUserDetails;
import it.abc.musical.security.Roles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Estrae identità e ruoli dall'Authentication (JWT bearer o sessione). */
public final class AuthUtil {

    private AuthUtil() {
    }

    /**
     * Solo le autorita' che rappresentano davvero un ruolo (prefisso ROLE_). Spring Security
     * aggiunge alle autorita' anche marcatori del metodo di login (es. FACTOR_PASSWORD dopo un
     * login con password): senza questo filtro finivano nel claim roles del JWT come se fossero
     * un ruolo in piu'.
     */
    public static Set<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
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

    /** True se l'utente ha almeno uno dei ruoli nella stringa comma-separated (null/blank = tutti). */
    public static boolean matchesTargetRoles(String targetRoles, Set<String> userRoles) {
        return matchesRoles(RoleCsv.parse(targetRoles), userRoles);
    }

    /** True se l'utente ha almeno uno dei ruoli ammessi; nessun ruolo ammesso = tutti. */
    public static boolean matchesRoles(Collection<String> allowedRoles, Set<String> userRoles) {
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }
        // Gli amministratori vedono comunque tutto (calendario, comunicazioni, archivio).
        if (userRoles.stream().anyMatch(Roles.ADMINS::contains)) {
            return true;
        }
        return allowedRoles.stream().anyMatch(userRoles::contains);
    }
}

package it.abc.musical.security;

import it.abc.musical.entities.User;
import it.abc.musical.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Al termine del login OAuth2 genera il JWT e reindirizza al frontend:
 * {frontendUrl}/auth/oauth2/callback?token={jwt}
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final String frontendUrl;
    private final long expirationSeconds;

    public OAuth2AuthenticationSuccessHandler(JwtTokenService jwtTokenService,
                                              UserRepository userRepository,
                                              @Value("${app.frontend-url}") String frontendUrl,
                                              @Value("${jwt.expiration}") long expirationSeconds) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl;
        this.expirationSeconds = expirationSeconds;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String email = extractEmail(authentication);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Utente OAuth2 non trovato: " + email));

        if (!user.isActive()) {
            getRedirectStrategy().sendRedirect(request, response,
                    frontendUrl + "/login?error=account_disabled");
            return;
        }

        String token = jwtTokenService.generateTokenForUser(user, expirationSeconds);
        getRedirectStrategy().sendRedirect(request, response,
                frontendUrl + "/auth/oauth2/callback?token=" + token);
    }

    private String extractEmail(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidc) {
            return oidc.getEmail();
        }
        if (principal instanceof OAuth2User oauth2) {
            return oauth2.getAttribute("email");
        }
        throw new IllegalStateException("Principal OAuth2 inatteso: " + principal.getClass());
    }
}

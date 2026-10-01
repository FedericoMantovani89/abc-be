package it.abc.musical.security;

import it.abc.musical.config.SocialLoginConfig.SocialProviders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Accesso con un provider social NON attivo (chiavi vuote nel .env): invece dell'errore 500 di
 * Spring Security ("Invalid Client Registration") si torna al login con un errore leggibile.
 * Non e' un @Component di proposito: lo monta SecurityConfig dentro la catena, una volta sola.
 */
public class SocialProviderGuardFilter extends OncePerRequestFilter {

    private static final String PREFISSO = "/oauth2/authorization/";

    private final SocialProviders providers;
    private final String frontendUrl;

    public SocialProviderGuardFilter(SocialProviders providers, String frontendUrl) {
        this.providers = providers;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith(PREFISSO) && !providers.isActive(path.substring(PREFISSO.length()))) {
            response.sendRedirect(frontendUrl + "/login?error=social_non_attivo");
            return;
        }
        chain.doFilter(request, response);
    }
}

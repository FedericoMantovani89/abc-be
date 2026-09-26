package it.abc.musical.security;

import it.abc.musical.entities.User;
import it.abc.musical.repositories.UserRepository;
import it.abc.musical.services.AuditLogService;
import it.abc.musical.util.EmailAddresses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        recordFailedLogin(request);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String error = exception instanceof DisabledException
                ? "ACCOUNT_NOT_VERIFIED"
                : "INVALID_CREDENTIALS";
        response.getWriter().write("{\"success\": false, \"error\": \"" + error + "\"}");
    }

    /**
     * Registro attivita': LOGIN_FAILED con l'IP e, se l'email tentata e' di un account,
     * il suo id (audit_logs non ha una colonna di testo libero). L'email tentata va nel log
     * dell'applicazione. Mai la password.
     */
    private void recordFailedLogin(HttpServletRequest request) {
        String email = EmailAddresses.normalize(request.getParameter("username"));
        Long userId = email == null || email.isEmpty() ? null
                : userRepository.findByEmailIgnoreCase(email).map(User::getId).orElse(null);
        auditLogService.record("LOGIN_FAILED", "User", userId);
        log.warn("Login fallito per '{}' da {}", forLog(email), ClientIp.of(request));
    }

    /** Il testo arriva da chiunque: niente a capo (righe di log false) e lunghezza limitata. */
    private static String forLog(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("[\r\n\t]", "_");
        return clean.length() > 255 ? clean.substring(0, 255) + "..." : clean;
    }
}

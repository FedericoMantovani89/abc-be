package it.abc.musical.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import it.abc.musical.config.AppProperties;
import it.abc.musical.config.Messages;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rate limiting sulle rotte di autenticazione: app.limits.auth-requests-per-hour (20) richieste/ora per IP del visitatore
 * (vedi {@link ClientIp}: il login arriva dal server Next, che inoltra l'IP in X-Forwarded-For).
 * Cache LRU in memoria (max 10.000 IP).
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_CACHED_IPS = 10_000;
    private final int requestsPerHour;

    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_CACHED_IPS;
                }
            });

    public RateLimitingFilter(AppProperties props) {
        this.requestsPerHour = props.getLimits().getAuthRequestsPerHour();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(ClientIp.of(request), ip -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerHour)
                        .refillGreedy(requestsPerHour, Duration.ofHours(1))
                        .build())
                .build());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String code = "auth.troppe.richieste";
            response.getWriter().write("{\"error\": \"" + Messages.text(code) + "\", \"code\": \"" + code + "\"}");
        }
    }
}

package it.abc.musical.services;

import it.abc.musical.entities.AuditLog;
import it.abc.musical.repositories.AuditLogRepository;
import it.abc.musical.util.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /** Registra un'azione admin; non solleva mai (l'audit non deve rompere l'operazione). */
    public void record(String action, String entityType, Long entityId) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                entry.setUserId(AuthUtil.userId(auth));
            }
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                entry.setIpAddress(forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr());
                entry.setUserAgent(request.getHeader("User-Agent"));
            }
            auditLogRepository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Audit log fallito per {} {} {}", action, entityType, entityId, e);
        }
    }
}

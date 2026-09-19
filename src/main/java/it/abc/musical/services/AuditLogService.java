package it.abc.musical.services;

import it.abc.musical.entities.AuditLog;
import it.abc.musical.repositories.AuditLogRepository;
import it.abc.musical.util.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Value("${app.audit.retention-months:12}")
    private int retentionMonths;

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

    /** Pulizia notturna dei log più vecchi della soglia di conservazione (informativa privacy). */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOldAuditLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(retentionMonths);
        int deleted = auditLogRepository.deleteCreatedBefore(cutoff);
        log.info("Purged {} audit log rows older than {} months", deleted, retentionMonths);
    }
}

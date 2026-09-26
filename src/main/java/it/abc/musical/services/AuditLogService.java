package it.abc.musical.services;

import it.abc.musical.entities.AuditLog;
import it.abc.musical.repositories.AuditLogRepository;
import it.abc.musical.security.ClientIp;
import it.abc.musical.util.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Se stesso, preso dal contesto invece che iniettato direttamente: serve a passare dal
     * proxy Spring quando {@link #record} chiama {@link #recordInNewTransaction}, altrimenti
     * la chiamata sarebbe una chiamata Java diretta (self-invocation) e {@code @Transactional}
     * non si attiverebbe. {@link ObjectProvider} rimanda la risoluzione: niente riferimento
     * circolare in fase di costruzione del bean.
     */
    private final ObjectProvider<AuditLogService> self;

    @Value("${app.audit.retention-months:12}")
    private int retentionMonths;

    /**
     * Registra un'azione admin; non solleva mai (l'audit non deve rompere l'operazione).
     * Chi chiama record() e' spesso gia' dentro il proprio {@code @Transactional} (i service,
     * dopo #9): un errore del database qui (es. valore troppo lungo per la colonna) marca
     * abortita l'intera transazione su quella connessione lato Postgres, non solo la riga di
     * registro, e ANCHE lato Spring/Hibernate la transazione JPA finisce segnata
     * rollback-only indipendentemente dal fatto che l'eccezione venga poi catturata qui.
     * {@link #recordInNewTransaction} isola la scrittura in una transazione propria
     * (REQUIRES_NEW): se fallisce, si annulla da sola e rilancia; il catch qui sotto la
     * ferma prima che raggiunga il chiamante, che non vede mai ne' l'eccezione ne' la sua
     * transazione segnata come da annullare.
     */
    public void record(String action, String entityType, Long entityId) {
        try {
            self.getObject().recordInNewTransaction(action, entityType, entityId);
        } catch (RuntimeException e) {
            log.warn("Audit log fallito per {} {} {}", action, entityType, entityId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordInNewTransaction(String action, String entityType, Long entityId) {
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
            String ip = ClientIp.of(request);
            // La colonna e' di 45 caratteri (IPv6): un'intestazione anomala non deve far fallire la riga.
            entry.setIpAddress(ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip);
            entry.setUserAgent(request.getHeader("User-Agent"));
        }
        auditLogRepository.save(entry);
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

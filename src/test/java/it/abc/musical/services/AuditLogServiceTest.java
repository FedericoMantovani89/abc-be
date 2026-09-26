package it.abc.musical.services;

import it.abc.musical.IntegrationTestBase;
import it.abc.musical.entities.AuditLog;
import it.abc.musical.repositories.AuditLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica contro un Postgres reale che la pulizia notturna elimina solo le righe più vecchie
 * della soglia di conservazione, senza toccare quelle più recenti: è il punto delicato del job,
 * una pulizia troppo aggressiva sarebbe peggio di nessuna pulizia.
 */
class AuditLogServiceTest extends IntegrationTestBase {

    @Autowired
    AuditLogService auditLogService;

    @Autowired
    AuditLogRepository auditLogRepository;

    @PersistenceContext
    EntityManager entityManager;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        // Soglia forzata a 12 mesi, indipendentemente dal default di configurazione.
        ReflectionTestUtils.setField(auditLogService, "retentionMonths", 12);
    }

    /**
     * created_at è @CreationTimestamp / updatable=false: per simulare una riga vecchia senza
     * aspettare 12 mesi veri, la si inserisce normalmente e poi la si retrodata con un UPDATE
     * nativo, fuori dal controllo di Hibernate sulla colonna. Richiede una transazione attiva
     * (vedi @Transactional sul metodo di test che la chiama).
     */
    AuditLog logCreatedAt(LocalDateTime createdAt) {
        AuditLog entry = new AuditLog();
        entry.setAction("LOGIN");
        AuditLog saved = auditLogRepository.saveAndFlush(entry);
        entityManager.createNativeQuery("UPDATE audit_logs SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, saved.getId())
                .executeUpdate();
        entityManager.clear();
        return saved;
    }

    @Test
    @Transactional
    void purgesOnlyRowsOlderThanTheRetentionThreshold() {
        LocalDateTime now = LocalDateTime.now();
        AuditLog old = logCreatedAt(now.minusMonths(13));
        AuditLog recent = logCreatedAt(now.minusMonths(1));

        auditLogService.purgeOldAuditLogs();

        assertThat(auditLogRepository.findById(old.getId())).isEmpty();
        assertThat(auditLogRepository.findById(recent.getId())).isPresent();
    }

    /**
     * "Incerto 2" dell'audit duplicazioni: entity_type e' VARCHAR(50) (V001), quindi un valore
     * piu' lungo fa fallire l'INSERT lato database (id e' IDENTITY: l'inserimento e' immediato,
     * non rimandato al flush). record() cattura sempre l'eccezione, ma su Postgres un'istruzione
     * fallita segna abortita l'INTERA transazione sulla connessione: senza una transazione
     * propria (REQUIRES_NEW), la prossima istruzione del chiamante — qui, di questo stesso
     * metodo, che sta al posto di un service come CalendarService.create() dopo lo spostamento
     * del registro nel service (punto 4) — fallirebbe a sua volta con "current transaction is
     * aborted", anche se l'eccezione di record() non e' mai arrivata fin qui.
     */
    @Test
    @Transactional
    void auditFailureRunsInItsOwnTransactionAndDoesNotAbortTheCallers() {
        auditLogService.record("CREATE", "X".repeat(51), 1L);

        Number roleCount = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM roles")
                .getSingleResult();
        assertThat(roleCount.longValue()).isGreaterThan(0);
    }
}

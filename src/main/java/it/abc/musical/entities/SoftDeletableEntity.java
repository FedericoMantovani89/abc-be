package it.abc.musical.entities;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Colonne comuni a tutte le entita' con cancellazione morbida: chi ha creato la riga, quando,
 * quando e' stata aggiornata l'ultima volta, e (se non nulla) quando e' stata cancellata.
 * {@code updated_by} non c'e' qui: {@code Folder} e {@code Document} non hanno questa colonna
 * (mai aggiunta) e una migrazione per uniformarle non varrebbe la pena — vedi {@link AuditableEntity}
 * per le entita' che invece la scrivono.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class SoftDeletableEntity {

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Cancellazione morbida: la riga resta, {@code deletedAt} la esclude dalle query attive. */
    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
    }
}

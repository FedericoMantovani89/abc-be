package it.abc.musical.entities;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/** {@link SoftDeletableEntity} piu' {@code updated_by}: chi ha fatto l'ultima modifica. */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity extends SoftDeletableEntity {

    @Column(name = "updated_by")
    private Long updatedBy;
}

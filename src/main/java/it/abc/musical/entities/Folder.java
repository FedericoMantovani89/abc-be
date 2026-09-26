package it.abc.musical.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "folders")
@Getter
@Setter
@NoArgsConstructor
public class Folder extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private Folder parentFolder;

    /**
     * Ruoli ammessi (tabella folder_roles); vuoto = tutti i soci. Valgono anche per tutto cio'
     * che sta sotto la cartella: la regola e' in MediaService.canAccess.
     */
    @ElementCollection
    @CollectionTable(name = "folder_roles", joinColumns = @JoinColumn(name = "folder_id"))
    @Column(name = "role_name", length = 50, nullable = false)
    private Set<String> allowedRoles = new LinkedHashSet<>();
}

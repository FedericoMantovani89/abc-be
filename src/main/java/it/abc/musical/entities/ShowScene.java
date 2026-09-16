package it.abc.musical.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "show_scenes")
@Getter
@Setter
@NoArgsConstructor
public class ShowScene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    /** Numerazione libera, es. "Atto I sc.2". */
    @Column(name = "scene_number", length = 50)
    private String sceneNumber;

    @Column(nullable = false)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /** Sottoinsieme di show_cast.role_name convocato nella scena. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "scene_cast_roles", joinColumns = @JoinColumn(name = "scene_id"))
    @Column(name = "role_name", nullable = false)
    private Set<String> castRoles = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

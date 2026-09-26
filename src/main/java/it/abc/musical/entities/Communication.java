package it.abc.musical.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "communications")
@Getter
@Setter
@NoArgsConstructor
public class Communication extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "communication_type_id")
    private CommunicationType communicationType;

    /** LOW | NORMAL | HIGH | URGENT */
    @Column(length = 20)
    private String priority = "NORMAL";

    private Boolean pinned = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Comma-separated; null = tutti i soci. */
    @Column(name = "target_roles", columnDefinition = "text")
    private String targetRoles;
}

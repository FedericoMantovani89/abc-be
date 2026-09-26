package it.abc.musical.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Nullable per utenti registrati solo via OAuth. */
    private String password;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /** 'google' | 'facebook' | null */
    @Column(name = "oauth_provider", length = 20)
    private String oauthProvider;

    @Column(name = "oauth_id")
    private String oauthId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** Valorizzata alla cancellazione logica dell'account; null = attivo. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
}

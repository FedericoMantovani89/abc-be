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
@Table(name = "calendar_events")
@Getter
@Setter
@NoArgsConstructor
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_type_id")
    private CalendarEventType eventType;

    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDatetime;

    @Column(name = "end_datetime")
    private LocalDateTime endDatetime;

    private String location;

    private String venue;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = false;

    @Column(name = "recurrence_pattern", length = 100)
    private String recurrencePattern;

    /** Link loose a events.id se il tipo è "Spettacolo" (nessun vincolo FK). */
    @Column(name = "public_event_id")
    private Long publicEventId;

    /** Comma-separated, es. "DIRECTOR,STAFF"; null = visibile a tutti i soci. */
    @Column(name = "target_roles", columnDefinition = "text")
    private String targetRoles;

    /** Solo per eventi di tipo "Prova". */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id")
    private Show show;

    /** Ruoli del cast convocati — fonte di verità per chi partecipa alla prova. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "calendar_event_rehearsal_roles", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "role_name", nullable = false)
    private Set<String> rehearsalRoles = new LinkedHashSet<>();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

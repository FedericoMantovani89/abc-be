package it.abc.musical.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "event_date", nullable = false)
    private LocalDateTime eventDate;

    @Column(name = "location_venue", nullable = false)
    private String locationVenue;

    @Column(name = "location_address", length = 500)
    private String locationAddress;

    @Column(name = "location_city", length = 100)
    private String locationCity;

    @Column(name = "location_province", length = 2)
    private String locationProvince;

    @Column(name = "poster_image_url", length = 512)
    private String posterImageUrl;

    /** Punto focale della locandina (percentuale 0..100), null = centro. */
    @Column(name = "hero_focus_x")
    private Integer heroFocusX;

    @Column(name = "hero_focus_y")
    private Integer heroFocusY;

    @Column(name = "booking_open_at")
    private LocalDateTime bookingOpenAt;

    @Column(name = "booking_close_at")
    private LocalDateTime bookingCloseAt;

    @Column(name = "booking_link", length = 512)
    private String bookingLink;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    /** Valorizzato se l'evento è la replica di uno spettacolo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id")
    private Show show;

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

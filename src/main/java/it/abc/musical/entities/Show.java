package it.abc.musical.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "shows")
@Getter
@Setter
@NoArgsConstructor
public class Show extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    /** HTML sanitizzato con Jsoup prima del salvataggio. */
    @Column(nullable = false, columnDefinition = "text")
    private String plot = "";

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 0;

    @Column(name = "age_recommendation")
    private Integer ageRecommendation;

    private String director;

    @Column(name = "set_designer")
    private String setDesigner;

    @Column(name = "costume_designer")
    private String costumeDesigner;

    private String choreographer;

    @Column(name = "hair_and_makeup")
    private String hairAndMakeup;

    private String producer;

    @Column(name = "production_year")
    private Integer productionYear;

    @Column(name = "trailer_url", length = 512)
    private String trailerUrl;

    @Column(name = "official_website_url", length = 512)
    private String officialWebsiteUrl;

    @Column(name = "reviews_url", length = 512)
    private String reviewsUrl;

    @Column(name = "social_media_url", length = 512)
    private String socialMediaUrl;

    @Column(name = "poster_image_url", length = 512)
    private String posterImageUrl;

    /** Punto focale della locandina (percentuale 0..100), null = centro. */
    @Column(name = "hero_focus_x")
    private Integer heroFocusX;

    @Column(name = "hero_focus_y")
    private Integer heroFocusY;

    /** Punto focale della locandina per mobile (percentuale 0..100), null = centro. Indipendente da hero_focus_x/y. */
    @Column(name = "hero_focus_mobile_x")
    private Integer heroFocusMobileX;

    @Column(name = "hero_focus_mobile_y")
    private Integer heroFocusMobileY;

    /** Fattore di zoom della locandina in hero (percentuale sul cover odierno), null = 100. */
    @Column(name = "hero_zoom_desktop")
    private Integer heroZoomDesktop;

    @Column(name = "hero_zoom_mobile")
    private Integer heroZoomMobile;

    /** Flag admin: se true (e con locandina) lo spettacolo compare nel Repertorio della home. */
    @Column(name = "show_in_home", nullable = false)
    private boolean showInHome = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_warnings", columnDefinition = "jsonb")
    private List<ContentWarning> contentWarnings = new ArrayList<>();

    @OneToMany(mappedBy = "show", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ShowCast> cast = new ArrayList<>();

    @OneToMany(mappedBy = "show", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private Set<ShowImage> images = new LinkedHashSet<>();
}

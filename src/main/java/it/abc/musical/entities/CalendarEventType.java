package it.abc.musical.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "calendar_event_types")
@Getter
@Setter
@NoArgsConstructor
public class CalendarEventType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Classe FontAwesome, es. "fa-microphone". */
    @Column(name = "icon_class", length = 100)
    private String iconClass;

    /** #RRGGBB */
    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(nullable = false)
    private boolean active = true;

    /** True per i tipi "Prova": il form mostra selezione spettacolo + ruoli del cast. */
    @Column(name = "is_rehearsal_type", nullable = false)
    private boolean rehearsalType = false;
}

package it.abc.musical.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Avvertenza di contenuto serializzata in JSONB su shows.content_warnings.
 * severity: info | warning | danger
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContentWarning {
    private String description;
    private String severity;
}

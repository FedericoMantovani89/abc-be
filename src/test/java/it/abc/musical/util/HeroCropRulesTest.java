package it.abc.musical.util;

import it.abc.musical.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regole del ritaglio della locandina in hero, uniche per spettacoli ed eventi (prima erano
 * scritte due volte, identiche, in {@code ShowService} e {@code EventService}).
 */
class HeroCropRulesTest {

    @Test
    void acceptsFocusWhenBothCoordinatesAreNull() {
        assertThatCode(() -> HeroCropRules.validateFocus(null, null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsFocusWhenBothCoordinatesAreInRange() {
        assertThatCode(() -> HeroCropRules.validateFocus(0, 100)).doesNotThrowAnyException();
    }

    @Test
    void rejectsFocusWithOnlyOneCoordinate() {
        assertThatThrownBy(() -> HeroCropRules.validateFocus(30, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Punto focale non valido");
    }

    @Test
    void rejectsFocusOutOfRange() {
        assertThatThrownBy(() -> HeroCropRules.validateFocus(-1, 50))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Punto focale non valido");
        assertThatThrownBy(() -> HeroCropRules.validateFocus(30, 101))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Punto focale non valido");
    }

    @Test
    void acceptsZoomWhenBothAreNull() {
        assertThatCode(() -> HeroCropRules.validateZoom(null, null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsZoomWhenIndependentlyInRange() {
        assertThatCode(() -> HeroCropRules.validateZoom(10, 300)).doesNotThrowAnyException();
    }

    @Test
    void rejectsZoomDesktopOutOfRange() {
        assertThatThrownBy(() -> HeroCropRules.validateZoom(9, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Zoom non valido");
    }

    @Test
    void rejectsZoomMobileOutOfRange() {
        assertThatThrownBy(() -> HeroCropRules.validateZoom(null, 301))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Zoom non valido");
    }
}

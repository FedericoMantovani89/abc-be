package it.abc.musical.util;

import it.abc.musical.exceptions.BadRequestException;

/**
 * Regole del ritaglio della locandina in hero (punto focale e zoom), condivise da spettacoli
 * ed eventi: stesse regole, stessi messaggi, un solo posto da cambiare se cambiano i limiti.
 */
public final class HeroCropRules {

    private HeroCropRules() {
    }

    /**
     * Punto focale (desktop o mobile, stessa regola per entrambi): entrambe le coordinate
     * assenti (centro) oppure entrambe in 0..100. Un solo valore presente, o fuori range, è un
     * input incoerente e va rifiutato qui invece che lasciarlo diventare un crop CSS
     * silenziosamente sbagliato lato frontend.
     */
    public static void validateFocus(Integer x, Integer y) {
        boolean bothNull = x == null && y == null;
        boolean bothInRange = x != null && y != null && x >= 0 && x <= 100 && y >= 0 && y <= 100;
        if (!bothNull && !bothInRange) {
            throw new BadRequestException("Punto focale non valido");
        }
    }

    /**
     * Fattore di zoom della locandina in hero: desktop e mobile sono indipendenti fra loro e
     * dal punto focale, ciascuno o assente (100, cioè invariato) o in 10..300.
     */
    public static void validateZoom(Integer desktop, Integer mobile) {
        if (!isValidZoom(desktop) || !isValidZoom(mobile)) {
            throw new BadRequestException("Zoom non valido");
        }
    }

    private static boolean isValidZoom(Integer zoom) {
        return zoom == null || (zoom >= 10 && zoom <= 300);
    }
}

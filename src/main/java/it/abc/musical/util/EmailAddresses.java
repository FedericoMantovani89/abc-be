package it.abc.musical.util;

import java.util.Locale;

/** Forma unica delle email salvate: senza spazi ai lati e in minuscolo. */
public final class EmailAddresses {

    private EmailAddresses() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

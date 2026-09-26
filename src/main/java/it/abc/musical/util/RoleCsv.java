package it.abc.musical.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Elenco di ruoli scritto come testo "RUOLO,RUOLO" (calendar_events.target_roles,
 * communications.target_roles): un solo modo di leggerlo e di scriverlo. Nomi senza spazi, in
 * maiuscolo, senza vuoti ne' doppioni; nessun ruolo = null, cioe' "tutti i soci". La forma
 * scritta rispetta il CHECK di V010 ('^[A-Z_]+(,[A-Z_]+)*$').
 */
public final class RoleCsv {

    private RoleCsv() {
    }

    /** Testo -> ruoli ripuliti, nell'ordine in cui compaiono. Null o vuoto -> lista vuota. */
    public static List<String> parse(String csv) {
        if (csv == null) {
            return List.of();
        }
        return clean(Stream.of(csv.split(",")));
    }

    /** Ruoli -> testo ripulito; nessun ruolo valido -> null. */
    public static String format(Collection<String> roles) {
        if (roles == null) {
            return null;
        }
        List<String> cleaned = clean(roles.stream());
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    /** Ripulisce un testo arrivato dal client prima di salvarlo. */
    public static String normalize(String csv) {
        return format(parse(csv));
    }

    private static List<String> clean(Stream<String> roles) {
        Set<String> result = new LinkedHashSet<>();
        roles.filter(r -> r != null)
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .filter(r -> !r.isEmpty())
                .forEach(result::add);
        return List.copyOf(result);
    }
}

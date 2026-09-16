package it.abc.musical.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Sanitizza l'HTML del campo plot prima del salvataggio:
 * solo tag di formattazione base e link http/https.
 */
public final class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.none()
            .addTags("h2", "h3", "p", "strong", "em", "ul", "ol", "li", "a", "br")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https");

    private HtmlSanitizer() {
    }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        return Jsoup.clean(html, SAFELIST);
    }
}

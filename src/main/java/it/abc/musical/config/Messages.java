package it.abc.musical.config;

import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

/**
 * Accesso ai testi di messages.properties anche dove non c'e' Spring (le eccezioni sono create
 * da codice statico e da test senza contesto). Nel server vero {@link #use} sostituisce la
 * sorgente con quella di {@link MessagesConfig}, che legge prima /config/messages.properties.
 */
public final class Messages {

    public static final Locale LOCALE = Locale.ITALY;

    private static volatile MessageSource source = build("classpath:messages");

    private Messages() {
    }

    static void use(MessageSource messageSource) {
        source = messageSource;
    }

    /** Il testo del codice; se il codice non esiste restituisce il codice stesso (mai un errore). */
    public static String text(String code, Object... args) {
        return source.getMessage(code, args, code, LOCALE);
    }

    static ReloadableResourceBundleMessageSource build(String... basenames) {
        ReloadableResourceBundleMessageSource messages = new ReloadableResourceBundleMessageSource();
        messages.setBasenames(basenames);
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }
}

package it.abc.musical.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unico punto che ricava l'IP del visitatore.
 * Il backend non e' raggiungibile da fuori (porta legata a 127.0.0.1): davanti ci sono Caddy
 * e il server Next, che mettono l'IP del visitatore come primo valore di X-Forwarded-For.
 * Intestazione assente o vuota = IP della connessione.
 * Con {@code server.forward-headers-strategy: framework} il ForwardedHeaderFilter ha gia'
 * spostato quel valore in {@code getRemoteAddr()} e tolto l'intestazione: il risultato e' lo stesso.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}

package it.abc.musical.exceptions;

import it.abc.musical.config.Messages;
import lombok.Getter;

/**
 * Errore con un codice stabile (es. {@code auth.email.gia.registrata}) e un messaggio italiano
 * preso da messages.properties. Il frontend legge il codice; il messaggio resta per chi non lo
 * conosce ancora. L'elenco dei codici sta in docs/codici-errore.md.
 */
@Getter
public abstract class CodedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    /** Numeri come testo (String.valueOf): MessageFormat metterebbe il separatore delle migliaia. */
    protected CodedException(String code, Object... args) {
        super(Messages.text(code, args));
        this.code = code;
    }
}

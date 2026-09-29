package it.abc.musical.exceptions;

import it.abc.musical.config.Messages;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I codici d'errore sono un contratto con il frontend: ogni codice usato nel codice deve avere il
 * suo testo in messages.properties ed essere elencato in docs/codici-errore.md, e viceversa.
 */
class CodiciErroreDocTest {

    private static final Pattern THROW = Pattern.compile(
            "new (?:BadRequest|Conflict|NotFound)Exception\\(\\s*\"([a-z][a-z0-9.]*)\"");
    private static final Pattern CONSTANT = Pattern.compile("static final String \\w+_ERROR = \"([a-z][a-z0-9.]*)\"");
    private static final Pattern DOC_ROW = Pattern.compile("^\\| `([a-z][a-z0-9.]*)` \\|", Pattern.MULTILINE);

    /** Codici che non nascono da un new XException: il gestore generico, il login e il limite di richieste. */
    private static final Set<String> FIXED = Set.of(
            "auth.account.non.verificato", "auth.credenziali.non.valide", "auth.troppe.richieste",
            "errore.dati.non.validi", "errore.corpo.non.leggibile", "errore.parametro.non.valido",
            "errore.parametro.obbligatorio", "errore.risorsa.non.trovata", "errore.vincolo.database",
            "errore.file.troppo.grande.limite", "errore.file.troppo.grande", "errore.interno",
            "validazione.obbligatorio", "validazione.email.non.valida", "validazione.lunghezza",
            "validazione.valore.non.valido", "validazione.password");

    @Test
    void everyCodeUsedInTheSourceHasAMessageAndIsDocumented() throws IOException {
        Set<String> used = new TreeSet<>(FIXED);
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (Pattern p : new Pattern[] {THROW, CONSTANT}) {
                    Matcher m = p.matcher(source);
                    while (m.find()) {
                        used.add(m.group(1));
                    }
                }
            }
        }
        Set<String> messages = new TreeSet<>(messageCodes());
        Set<String> documented = new TreeSet<>();
        Matcher doc = DOC_ROW.matcher(Files.readString(Path.of("docs/codici-errore.md"), StandardCharsets.UTF_8));
        while (doc.find()) {
            documented.add(doc.group(1));
        }

        assertThat(messages).as("codici usati ma senza testo in messages.properties").containsAll(used);
        assertThat(used).as("testi in messages.properties che nessuno usa").containsAll(messages);
        assertThat(documented).as("docs/codici-errore.md deve elencare gli stessi codici").isEqualTo(messages);
    }

    @Test
    void everyMessageIsAWellFormedItalianSentence() throws IOException {
        for (String code : messageCodes()) {
            String raw = rawMessage(code);
            String text = raw.contains("{0}") ? Messages.text(code, "A", "B", "C", "D") : Messages.text(code);
            assertThat(text).as(code).isNotBlank().isNotEqualTo(code).doesNotContain("{0}", "{1}", "''");
            // con segnaposto l'apostrofo va raddoppiato, senza segnaposto no
            assertThat(raw.contains("{0}") || !raw.contains("''")).as(code + ": '' senza segnaposto").isTrue();
        }
    }

    @Test
    void anExceptionCarriesItsCodeAndTheOldMessage() {
        BadRequestException e = new BadRequestException("utenti.dimensione.pagina", "100");

        assertThat(e.getCode()).isEqualTo("utenti.dimensione.pagina");
        assertThat(e.getMessage()).isEqualTo("La dimensione della pagina deve essere fra 1 e 100");
        assertThat(new ConflictException("media.cartella.duplicata", "COPIONI").getMessage())
                .isEqualTo("Esiste gia' una cartella «COPIONI» in quella posizione.");
    }

    @Test
    void anUnknownCodeStillProducesAMessage() {
        assertThat(new NotFoundException("codice.che.non.esiste").getMessage()).isEqualTo("codice.che.non.esiste");
    }

    private static String rawMessage(String code) throws IOException {
        Properties p = new Properties();
        try (var in = Files.newBufferedReader(Path.of("src/main/resources/messages.properties"), StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p.getProperty(code);
    }

    private static Set<String> messageCodes() throws IOException {
        Properties p = new Properties();
        try (var in = Files.newBufferedReader(Path.of("src/main/resources/messages.properties"), StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p.stringPropertyNames();
    }
}

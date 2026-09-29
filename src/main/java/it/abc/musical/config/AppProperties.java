package it.abc.musical.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Tutta la configurazione dell'applicazione sotto il prefisso {@code app}, validata all'avvio:
 * se manca un valore obbligatorio il server non parte e il messaggio dice quale proprieta'.
 * I valori arrivano da application.yml (che legge le variabili d'ambiente del .env) e possono
 * essere sovrascritti in /config/application-prod.yml senza toccare il pacchetto.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("app")
public class AppProperties {

    /** Indirizzo pubblico del backend: serve ai link nelle email (verifica, locandina) e al redirect OAuth2. */
    @NotBlank
    private String baseUrl;

    /** Indirizzo pubblico del sito (frontend): e' anche l'unica origine accettata dal CORS. */
    @NotBlank
    private String frontendUrl;

    @NotBlank
    private String uploadDir;

    /** Cartella della configurazione esterna (application-prod.yml, messages.properties, email/). */
    @NotBlank
    private String configDir = "/config";

    @Valid
    @NotNull
    private Upload upload = new Upload();

    @Valid
    @NotNull
    private Audit audit = new Audit();

    @Valid
    @NotNull
    private Admin admin = new Admin();

    @Valid
    @NotNull
    private Thumbs thumbs = new Thumbs();

    @Valid
    @NotNull
    private Jwt jwt = new Jwt();

    @Valid
    @NotNull
    private Security security = new Security();

    @Valid
    @NotNull
    private Mail mail = new Mail();

    @Valid
    @NotNull
    private Associazione associazione = new Associazione();

    @Valid
    @NotNull
    private Cors cors = new Cors();

    @Valid
    @NotNull
    private Limits limits = new Limits();

    @Getter
    @Setter
    public static class Upload {
        @Min(1)
        private int chunkSizeMb = 8;
        @Min(1)
        private long sessionTtlHours = 24;
    }

    @Getter
    @Setter
    public static class Audit {
        @Min(1)
        private int retentionMonths = 12;
    }

    /** Admin iniziale: facoltativo (senza, AdminSeeder non crea nulla). */
    @Getter
    @Setter
    public static class Admin {
        private String email = "";
        private String password = "";
    }

    @Getter
    @Setter
    public static class Thumbs {
        @NotBlank
        private String ffmpeg = "ffmpeg";
    }

    @Getter
    @Setter
    public static class Jwt {
        @NotBlank
        private String secret;
        @Min(1)
        private long expiration = 3600;
        @Min(1)
        private long remembermeExpiration = 2_592_000;
        @NotBlank
        private String issuer = "abc-musical";
    }

    @Getter
    @Setter
    public static class Security {
        @NotBlank
        private String rememberMeKey;
    }

    @Getter
    @Setter
    public static class Mail {
        /** Indirizzo mittente: per Aruba deve coincidere con l'utente SMTP. */
        @NotBlank
        private String from;
        @NotBlank
        private String fromName = "ABC Musical Company";
        /** Facoltativo: vuoto = nessun Reply-To (le email dicono "non rispondere"). */
        private String replyTo = "";
        @NotBlank
        private String verificationSubject;
        @NotBlank
        private String resetSubject;
        /** true = SSL diretto (porta 465); false = STARTTLS (porta 587). */
        private boolean ssl = false;
        @Min(1000)
        private int connectionTimeoutMs = 10_000;
        @Min(1000)
        private int readTimeoutMs = 10_000;
        @Min(1000)
        private int writeTimeoutMs = 10_000;
        /** Cartella dei modelli email che hanno la precedenza su quelli del pacchetto. */
        @NotBlank
        private String templatesDir = "/config/email";
    }

    @Getter
    @Setter
    public static class Associazione {
        @NotBlank
        private String nome;
        @NotBlank
        private String email;
        @NotBlank
        private String sede;
        @NotBlank
        private String cf;
        @NotBlank
        private String piva;
        /** "DA_INSERIRE" finche' ABC non comunica il numero. */
        @NotBlank
        private String runts = "DA_INSERIRE";
    }

    @Getter
    @Setter
    public static class Cors {
        /** Origini ammesse oltre al sito (solo sviluppo, es. http://localhost:3000). Vuota di default. */
        private List<String> extraOrigins = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Limits {
        @Min(1)
        private int verificationLinkHours = 24;
        @Min(1)
        private int resetLinkHours = 1;
        /** Richieste per ora e per IP su /api/auth/**. */
        @Min(1)
        @Max(100_000)
        private int authRequestsPerHour = 20;
    }
}

package it.abc.musical.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Email automatiche di verifica dell'iscrizione e di reimpostazione password. L'HTML viene da
 * resources/email/account-email.html (bozze v2 approvate il 28/09/2026): tabelle, stili in linea,
 * testata scura con la locandina di uno spettacolo scelto a caso a ogni invio. Ogni mail ha anche
 * la parte in solo testo.
 */
@Slf4j
@Service
public class EmailService {

    private static final String FROM = "noreply@abcmusical.it";
    static final String VERIFICATION_SUBJECT = "Conferma il tuo indirizzo email — ABC Musical Company";
    static final String RESET_SUBJECT = "Reimposta la password — ABC Musical Company";
    /** Logo PNG servito dal frontend: Gmail e Outlook non mostrano gli SVG. */
    static final String LOGO_PATH = "/assets/email/logo-email.png";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final JavaMailSender mailSender;
    private final EmailPosterService emailPosterService;
    private final String template;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender, EmailPosterService emailPosterService) {
        this.mailSender = mailSender;
        this.emailPosterService = emailPosterService;
        try (var in = new ClassPathResource("email/account-email.html").getInputStream()) {
            this.template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Modello delle email non trovato", e);
        }
    }

    /** Oggetto, HTML e testo semplice di una mail. */
    record Mail(String subject, String html, String text) {
    }

    @Async
    public void sendVerificationEmail(String toEmail, String firstName, String token) {
        send(toEmail, verificationMail(firstName, token));
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String firstName, String token) {
        send(toEmail, passwordResetMail(firstName, token));
    }

    Mail verificationMail(String firstName, String token) {
        String link = baseUrl + "/api/auth/verify?token=" + token;
        String name = cleanName(firstName);
        String heading = name == null ? "Ti diamo il benvenuto!" : "Ciao " + name + ", ti diamo il benvenuto!";
        String note = "Il link vale 24 ore. Se non hai chiesto tu l'iscrizione, ignora questa email: "
                + "senza conferma l'account non viene attivato.";
        String html = render(Map.of(
                "subject", VERIFICATION_SUBJECT,
                "preheader", "Un clic per confermare l'indirizzo email e completare l'iscrizione ad ABC Musical Company.",
                "eyebrow", "Conferma l'iscrizione",
                "heading", heading,
                "intro", "Grazie per l'iscrizione al sito di <strong>ABC Musical Company</strong>. "
                        + "Per completarla, conferma che questo indirizzo email &egrave; tuo:",
                "buttonLabel", "Conferma la mia email",
                "link", link,
                "note", note), posterShow());
        String text = heading + "\n\n"
                + "Grazie per l'iscrizione al sito di ABC Musical Company. "
                + "Per completarla, conferma che questo indirizzo email è tuo aprendo questo link:\n\n"
                + link + "\n\n" + note + "\n\n" + footerText();
        return new Mail(VERIFICATION_SUBJECT, html, text);
    }

    Mail passwordResetMail(String firstName, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String name = cleanName(firstName);
        String heading = name == null
                ? "Ecco il link per la nuova password"
                : "Ciao " + name + ", ecco il link per la nuova password";
        String note = "Il link vale 1 ora e si può usare una volta sola. Se non hai fatto tu la richiesta, "
                + "ignora questa email: la tua password attuale resta valida.";
        String html = render(Map.of(
                "subject", RESET_SUBJECT,
                "preheader", "Hai chiesto di cambiare la password del sito ABC Musical Company. Il link vale un'ora.",
                "eyebrow", "Accesso al sito",
                "heading", heading,
                "intro", "Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account "
                        + "sul sito di <strong>ABC Musical Company</strong>. Scegli la nuova password dal tasto qui sotto:",
                "buttonLabel", "Scegli una nuova password",
                "link", link,
                "note", note), posterShow());
        String text = heading + "\n\n"
                + "Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account "
                + "sul sito di ABC Musical Company. Scegli la nuova password da questo link:\n\n"
                + link + "\n\n" + note + "\n\n" + footerText();
        return new Mail(RESET_SUBJECT, html, text);
    }

    /**
     * Riempie il modello in una sola passata (un valore che contiene "{{...}}" non viene
     * rielaborato). Testi e URL sono escapati qui, tranne "intro" che e' HTML scritto nel codice.
     */
    private String render(Map<String, String> texts, Optional<Long> posterShow) {
        String site = trimSlash(frontendUrl);
        Map<String, String> values = new HashMap<>();
        texts.forEach((k, v) -> values.put(k, k.equals("intro") ? v : escape(v)));
        values.put("siteUrl", escape(site));
        values.put("siteHost", escape(host(site)));
        values.put("logoUrl", escape(site + LOGO_PATH));
        if (posterShow.isPresent()) {
            String poster = trimSlash(baseUrl) + "/api/public/email/poster.jpg?show=" + posterShow.get() + "&v=";
            values.put("bandRow", bandRow(escape(poster + "band")));
            values.put("posterCell", posterCell(escape(poster + "side")));
            values.put("headCellClass", " class=\"head-cell\"");
            values.put("headTextOpen", "<td class=\"head-text\" width=\"380\" valign=\"middle\" style=\"width:380px; "
                    + "padding:28px 0 26px 32px; font-family:Georgia, 'Times New Roman', serif;\">");
        } else {
            // Nessuna locandina: niente fascia ne' colonna, il titolo prende tutta la larghezza.
            values.put("bandRow", "");
            values.put("posterCell", "");
            values.put("headCellClass", "");
            values.put("headTextOpen", "<td class=\"head-text-solo\" valign=\"middle\" style=\"padding:28px 32px 26px 32px; "
                    + "font-family:Georgia, 'Times New Roman', serif;\">");
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            if (value == null) {
                throw new IllegalStateException("Segnaposto senza valore nel modello email: " + m.group(1));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Fascia solo telefono: nascosta ovunque (Outlook compreso, mso-hide), la mostra solo la media query.
     */
    private static String bandRow(String src) {
        return """
                    <!--[if !mso]><!-->
                    <tr class="poster-band" style="display:none; max-height:0; overflow:hidden; mso-hide:all;"><td bgcolor="#0F0F23" style="background-color:#0F0F23; font-size:0; line-height:0; border-radius:8px 8px 0 0;">
                      <img src="%s" width="351" alt="" style="display:none; max-height:0; width:100%%; height:auto; border:0; outline:none;">
                    </td></tr>
                    <!--<![endif]-->""".formatted(src);
    }

    /** Locandina laterale: decorativa (alt vuoto), misure fisse: con immagini bloccate resta il fondo scuro. */
    private static String posterCell(String src) {
        return """
                        <td class="poster-side" width="220" valign="top" align="right" style="width:220px; font-size:0; line-height:0;">
                          <img src="%s" width="220" height="293" alt="" style="display:block; width:220px; height:293px; border:0; outline:none;">
                        </td>""".formatted(src);
    }

    /** Un errore nella scelta della locandina non blocca la mail: esce senza immagine. */
    private Optional<Long> posterShow() {
        try {
            return emailPosterService.randomShowId();
        } catch (RuntimeException e) {
            log.warn("Locandina per email non scelta: {}", e.toString());
            return Optional.empty();
        }
    }

    private String footerText() {
        String site = trimSlash(frontendUrl);
        return "ABC Musical Company\nABC – Attori Ballerini Cantanti APS · Verona · dal 1998\n" + site + "\n\n"
                + "Hai ricevuto questa email perché qualcuno ha usato il tuo indirizzo sul nostro sito. "
                + "È un messaggio automatico: non rispondere.";
    }

    private static String cleanName(String firstName) {
        return firstName == null || firstName.isBlank() ? null : firstName.strip();
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null ? host : url;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private void send(String to, Mail mail) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setFrom(FROM);
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
            mailSender.send(message);
            log.info("Email inviata a {}: {}", to, mail.subject());
        } catch (MessagingException | RuntimeException e) {
            // Non blocchiamo il flusso: l'utente può richiedere un nuovo invio.
            log.error("Invio email fallito verso {}: {}", to, e.getMessage());
        }
    }
}

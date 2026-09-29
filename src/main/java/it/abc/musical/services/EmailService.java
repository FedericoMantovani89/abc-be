package it.abc.musical.services;

import it.abc.musical.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Email automatiche di verifica dell'iscrizione e di reimpostazione password. Testi e HTML stanno
 * nei modelli FreeMarker di resources/email (verifica.ftlh, reset.ftlh, layout.ftlh e le versioni
 * solo testo .txt.ftl), sovrascrivibili in /config/email: qui restano solo i valori. Ogni mail ha
 * la parte HTML (testata scura con la locandina di uno spettacolo scelto a caso) e quella in solo testo.
 */
@Slf4j
@Service
public class EmailService {

    /** Logo PNG servito dal frontend: Gmail e Outlook non mostrano gli SVG. */
    static final String LOGO_PATH = "/assets/email/logo-email.png";

    private final JavaMailSender mailSender;
    private final EmailPosterService emailPosterService;
    private final EmailTemplates templates;
    private final AppProperties props;

    public EmailService(JavaMailSender mailSender, EmailPosterService emailPosterService,
                        EmailTemplates templates, AppProperties props) {
        this.mailSender = mailSender;
        this.emailPosterService = emailPosterService;
        this.templates = templates;
        this.props = props;
    }

    /** Oggetto, HTML e testo semplice di una mail. */
    record Mail(String subject, String html, String text) {
    }

    @PostConstruct
    void logSender() {
        log.info("Email inviate da {} <{}>", props.getMail().getFromName(), props.getMail().getFrom());
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
        String link = props.getBaseUrl() + "/api/auth/verify?token=" + token;
        return compose("verifica", props.getMail().getVerificationSubject(), firstName, link,
                props.getLimits().getVerificationLinkHours());
    }

    Mail passwordResetMail(String firstName, String token) {
        String link = props.getFrontendUrl() + "/reset-password?token=" + token;
        return compose("reset", props.getMail().getResetSubject(), firstName, link,
                props.getLimits().getResetLinkHours());
    }

    /** Riempie i modelli {@code <nome>.ftlh} e {@code <nome>.txt.ftl} con gli stessi valori. */
    private Mail compose(String template, String subject, String firstName, String link, int hours) {
        String site = trimSlash(props.getFrontendUrl());
        Map<String, Object> model = new HashMap<>();
        model.put("subject", subject);
        model.put("link", link);
        model.put("hours", hours);
        model.put("siteUrl", site);
        model.put("siteHost", host(site));
        model.put("logoUrl", site + LOGO_PATH);
        String name = cleanName(firstName);
        if (name != null) {
            model.put("name", name);
        }
        posterShow().ifPresent(show -> {
            String poster = trimSlash(props.getBaseUrl()) + "/api/public/email/poster.jpg?show=" + show + "&v=";
            model.put("poster", Map.of("band", poster + "band", "side", poster + "side"));
        });
        String html = templates.render(template + ".ftlh", model);
        String text = templates.render(template + ".txt.ftl", model).stripTrailing();
        return new Mail(subject, html, text);
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

    private static String cleanName(String firstName) {
        return firstName == null || firstName.isBlank() ? null : firstName.strip();
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
            helper.setFrom(props.getMail().getFrom(), props.getMail().getFromName());
            if (!props.getMail().getReplyTo().isBlank()) {
                helper.setReplyTo(props.getMail().getReplyTo());
            }
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
            mailSender.send(message);
            log.info("Email inviata a {}: {}", to, mail.subject());
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            // Non blocchiamo il flusso: l'utente può richiedere un nuovo invio.
            log.error("Invio email fallito verso {}: {}", to, e.getMessage());
        }
    }
}

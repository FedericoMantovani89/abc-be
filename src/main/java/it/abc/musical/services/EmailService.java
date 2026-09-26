package it.abc.musical.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String FROM = "noreply@abcmusical.it";

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Async
    public void sendVerificationEmail(String toEmail, String firstName, String token) {
        String link = baseUrl + "/api/auth/verify?token=" + token;
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto;">
                  <h2 style="color:#1a1a2e;">Benvenuto in ABC Musical Company%s!</h2>
                  <p>Grazie per esserti registrato. Per attivare il tuo account clicca il pulsante qui sotto:</p>
                  <p style="text-align:center; margin: 28px 0;">
                    <a href="%s" style="background:#c9a227; color:#1a1a2e; padding: 12px 28px;
                       text-decoration:none; border-radius:4px; font-weight:bold;">VERIFICA IL TUO ACCOUNT</a>
                  </p>
                  <p style="color:#666; font-size:13px;">Se il pulsante non funziona, copia questo link nel browser:<br>%s</p>
                  <p style="color:#666; font-size:13px;">Il link scade tra 24 ore. Se non ti sei registrato tu, ignora questa email.</p>
                </div>
                """.formatted(firstName != null ? ", " + HtmlUtils.htmlEscape(firstName) : "", link, link);
        send(toEmail, "Verifica il tuo account — ABC Musical Company", html);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String firstName, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto;">
                  <h2 style="color:#1a1a2e;">Reimposta la tua password</h2>
                  <p>Ciao%s, abbiamo ricevuto una richiesta di reset della password per il tuo account.</p>
                  <p style="text-align:center; margin: 28px 0;">
                    <a href="%s" style="background:#c9a227; color:#1a1a2e; padding: 12px 28px;
                       text-decoration:none; border-radius:4px; font-weight:bold;">REIMPOSTA PASSWORD</a>
                  </p>
                  <p style="color:#666; font-size:13px;">Se il pulsante non funziona, copia questo link nel browser:<br>%s</p>
                  <p style="color:#666; font-size:13px;">Il link scade tra 1 ora. Se non hai richiesto tu il reset, ignora questa email.</p>
                </div>
                """.formatted(firstName != null ? " " + HtmlUtils.htmlEscape(firstName) : "", link, link);
        send(toEmail, "Reimposta la tua password — ABC Musical Company", html);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(FROM);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email inviata a {}: {}", to, subject);
        } catch (MessagingException | RuntimeException e) {
            // Non blocchiamo il flusso: l'utente può richiedere un nuovo invio.
            log.error("Invio email fallito verso {}: {}", to, e.getMessage());
        }
    }
}

package it.abc.musical.services;

import it.abc.musical.config.AppProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * L'HTML prodotto dai modelli FreeMarker deve essere IDENTICO, carattere per carattere, a quello
 * che produceva il vecchio modello con i segnaposto (bozze v2 del 28/09/2026). I file in
 * test/resources/email-golden sono le mail di allora, con gli stessi dati di prova.
 */
class EmailGoldenTest {

    private static final String TOKEN_VERIFICA = "esempio-7f3c9a1e-2b4d-4c8e-9a61-0d5e8b2f4a17";
    private static final String TOKEN_RESET = "esempio-3a9d5c2f-8e71-4b06-b2c4-6f1e0a7d9c38";

    private final EmailPosterService posters = mock(EmailPosterService.class);
    private final EmailService emailService = service();

    private EmailService service() {
        AppProperties props = EmailServiceTest.testProps();
        props.setBaseUrl("https://attoriballerinicantanti.nibius.duckdns.org");
        props.setFrontendUrl("https://attoriballerinicantanti.nibius.duckdns.org");
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        return new EmailService(sender, posters, new EmailTemplates(props), props);
    }

    @Test
    void verificationMailIsIdenticalToTheApprovedOne() throws IOException {
        when(posters.randomShowId()).thenReturn(Optional.of(1L));

        assertThat(emailService.verificationMail("Mario", TOKEN_VERIFICA).html())
                .isEqualTo(golden("verifica.html"));
    }

    @Test
    void resetMailIsIdenticalToTheApprovedOne() throws IOException {
        when(posters.randomShowId()).thenReturn(Optional.of(2L));

        assertThat(emailService.passwordResetMail("Mario", TOKEN_RESET).html())
                .isEqualTo(golden("reset.html"));
    }

    @Test
    void mailWithoutPosterIsIdenticalToTheApprovedOne() throws IOException {
        when(posters.randomShowId()).thenReturn(Optional.empty());

        assertThat(emailService.verificationMail("Mario", TOKEN_VERIFICA).html())
                .isEqualTo(golden("verifica-senza-locandina.html"));
    }

    private static String golden(String name) throws IOException {
        try (var in = new ClassPathResource("email-golden/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}

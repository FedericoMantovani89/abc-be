package it.abc.musical.services;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HTML delle mail v2 (bozze approvate il 28/09/2026): testi, link, locandina e nome escapato. */
class EmailServiceTest {

    private static final String HOSTILE_NAME = "<a href=\"https://truffa.example\">clicca qui</a>";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailPosterService posters = mock(EmailPosterService.class);
    private final EmailService emailService = new EmailService(mailSender, posters);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "baseUrl", "https://api.abc.example");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://abc.example");
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        when(posters.randomShowId()).thenReturn(Optional.of(7L));
    }

    @Test
    void verificationEmailEscapesTheName() {
        String html = emailService.verificationMail(HOSTILE_NAME, "tok").html();

        assertThat(html).doesNotContain("truffa.example\">");
        assertThat(html).contains("Ciao &lt;a href=&quot;https://truffa.example&quot;&gt;clicca qui&lt;/a&gt;, ti diamo il benvenuto!");
    }

    @Test
    void resetEmailEscapesTheName() {
        String html = emailService.passwordResetMail(HOSTILE_NAME, "tok").html();

        assertThat(html).doesNotContain("truffa.example\">");
        assertThat(html).contains("Ciao &lt;a href=");
    }

    @Test
    void nameThatLooksLikeAPlaceholderStaysText() {
        String html = emailService.verificationMail("{{link}}", "tok").html();

        assertThat(html).contains("Ciao {{link}}, ti diamo il benvenuto!");
    }

    @Test
    void verificationEmailHasSubjectLinkAndPoster() {
        EmailService.Mail mail = emailService.verificationMail("Mario", "tok-123");

        assertThat(mail.subject()).isEqualTo("Conferma il tuo indirizzo email — ABC Musical Company");
        assertThat(mail.html())
                .contains("<title>Conferma il tuo indirizzo email — ABC Musical Company</title>")
                .contains("Ciao Mario, ti diamo il benvenuto!")
                .contains("href=\"https://api.abc.example/api/auth/verify?token=tok-123\"")
                .contains(">Conferma la mia email</a>")
                .contains("src=\"https://abc.example/assets/email/logo-email.png\"")
                .contains("src=\"https://api.abc.example/api/public/email/poster.jpg?show=7&amp;v=side\"")
                .contains("src=\"https://api.abc.example/api/public/email/poster.jpg?show=7&amp;v=band\"")
                .contains(">abc.example</a>")
                .doesNotContain("{{");
        assertThat(mail.text()).contains("https://api.abc.example/api/auth/verify?token=tok-123");
        assertNoMembersAreaPromises(mail);
    }

    @Test
    void resetEmailHasSubjectAndFrontendLink() {
        EmailService.Mail mail = emailService.passwordResetMail(null, "tok-9");

        assertThat(mail.subject()).isEqualTo("Reimposta la password — ABC Musical Company");
        assertThat(mail.html())
                .contains("Ecco il link per la nuova password</h1>")
                .contains("href=\"https://abc.example/reset-password?token=tok-9\"")
                .contains(">Scegli una nuova password</a>")
                .doesNotContain("Ciao ")
                .doesNotContain("{{");
        assertThat(mail.text()).contains("https://abc.example/reset-password?token=tok-9");
        assertNoMembersAreaPromises(mail);
    }

    @Test
    void withoutPostersTheMailHasNoImageButTheLogo() {
        when(posters.randomShowId()).thenReturn(Optional.empty());

        String html = emailService.verificationMail("Mario", "tok").html();

        assertThat(html).doesNotContain("poster.jpg").doesNotContain("class=\"poster-side\"")
                .doesNotContain("class=\"poster-band\"").doesNotContain("width=\"380\"")
                .contains("class=\"head-text-solo\"")
                .contains("logo-email.png")
                .doesNotContain("{{");
    }

    @Test
    void aFailingPosterChoiceDoesNotStopTheMail() {
        when(posters.randomShowId()).thenThrow(new IllegalStateException("database giu'"));

        assertThat(emailService.passwordResetMail("Mario", "tok").html()).doesNotContain("poster.jpg");
    }

    @Test
    void sentMessageHasSubjectHtmlAndTextParts() throws Exception {
        emailService.sendVerificationEmail("socio@example.com", "Mario", "tok");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Conferma il tuo indirizzo email — ABC Musical Company");
        String raw = new String(message.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(message.getContent()).isInstanceOf(MimeMultipart.class);
        assertThat(contentTypes((MimeMultipart) message.getContent())).contains("text/plain", "text/html");
        assertThat(raw).isNotEmpty();
    }

    /**
     * Scrive le due mail vere (con e senza locandina) e le locandine sfumate per le schermate di
     * confronto con le bozze: verifica usa lo spettacolo 1, reset il 2.
     * mvn test -Dtest=EmailServiceTest#writeSamples -Demail.samples.dir=... [-Demail.samples.posters=a.jpg,b.jpg]
     */
    @Test
    @EnabledIfSystemProperty(named = "email.samples.dir", matches = ".+")
    void writeSamples() throws Exception {
        Path dir = Path.of(System.getProperty("email.samples.dir"));
        Files.createDirectories(dir);
        ReflectionTestUtils.setField(emailService, "baseUrl", "https://attoriballerinicantanti.nibius.duckdns.org");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://attoriballerinicantanti.nibius.duckdns.org");
        String posterList = System.getProperty("email.samples.posters", "");
        int n = 0;
        for (String poster : posterList.split(",")) {
            if (poster.isBlank()) {
                continue;
            }
            n++;
            BufferedImage source = ImageIO.read(Path.of(poster.strip()).toFile());
            for (EmailPosterService.Variant v : EmailPosterService.Variant.values()) {
                ImageIO.write(EmailPosterService.render(source, v), "jpg",
                        dir.resolve("poster-" + n + "-" + v.param() + ".jpg").toFile());
            }
        }
        when(posters.randomShowId()).thenReturn(Optional.of(1L));
        Files.writeString(dir.resolve("verifica.html"),
                emailService.verificationMail("Mario", "esempio-7f3c9a1e-2b4d-4c8e-9a61-0d5e8b2f4a17").html());
        when(posters.randomShowId()).thenReturn(Optional.of(2L));
        Files.writeString(dir.resolve("reset.html"),
                emailService.passwordResetMail("Mario", "esempio-3a9d5c2f-8e71-4b06-b2c4-6f1e0a7d9c38").html());
        when(posters.randomShowId()).thenReturn(Optional.empty());
        Files.writeString(dir.resolve("verifica-senza-locandina.html"),
                emailService.verificationMail("Mario", "esempio-7f3c9a1e-2b4d-4c8e-9a61-0d5e8b2f4a17").html());
    }

    private static void assertNoMembersAreaPromises(EmailService.Mail mail) {
        for (String text : new String[] {mail.html(), mail.text()}) {
            assertThat(text.toLowerCase()).doesNotContain("area soci").doesNotContain("amministrator")
                    .doesNotContain("cosa succede dopo");
        }
    }

    private static java.util.List<String> contentTypes(MimeMultipart multipart) throws Exception {
        java.util.List<String> types = new java.util.ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            var part = multipart.getBodyPart(i);
            if (part.getContent() instanceof MimeMultipart nested) {
                types.addAll(contentTypes(nested));
            } else {
                types.add(part.getContentType().split(";")[0].trim());
            }
        }
        return types;
    }
}

package it.abc.musical.services;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import it.abc.musical.config.AppProperties;

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
    private final AppProperties props = testProps();
    private final EmailService emailService =
            new EmailService(mailSender, posters, new EmailTemplates(props), props);

    @BeforeEach
    void setUp() {
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
        String html = emailService.verificationMail("{{link}} ${link}", "tok").html();

        assertThat(html).contains("Ciao {{link}} ${link}, ti diamo il benvenuto!");
    }

    @Test
    void plainTextPartsAreExactlyTheOnesOfBefore() {
        EmailService.Mail verifica = emailService.verificationMail("Mario", "tok-1");
        assertThat(verifica.text()).isEqualTo("""
                Ciao Mario, ti diamo il benvenuto!

                Grazie per l'iscrizione al sito di ABC Musical Company. Per completarla, conferma che questo indirizzo email è tuo aprendo questo link:

                https://api.abc.example/api/auth/verify?token=tok-1

                Il link vale 24 ore. Se non hai chiesto tu l'iscrizione, ignora questa email: senza conferma l'account non viene attivato.

                ABC Musical Company
                ABC – Attori Ballerini Cantanti APS · Verona · dal 1998
                https://abc.example

                Hai ricevuto questa email perché qualcuno ha usato il tuo indirizzo sul nostro sito. È un messaggio automatico: non rispondere.""");

        EmailService.Mail reset = emailService.passwordResetMail(null, "tok-2");
        assertThat(reset.text()).isEqualTo("""
                Ecco il link per la nuova password

                Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account sul sito di ABC Musical Company. Scegli la nuova password da questo link:

                https://abc.example/reset-password?token=tok-2

                Il link vale 1 ora e si può usare una volta sola. Se non hai fatto tu la richiesta, ignora questa email: la tua password attuale resta valida.

                ABC Musical Company
                ABC – Attori Ballerini Cantanti APS · Verona · dal 1998
                https://abc.example

                Hai ricevuto questa email perché qualcuno ha usato il tuo indirizzo sul nostro sito. È un messaggio automatico: non rispondere.""");
    }

    @Test
    void scriptInTheNameArrivesAsTextInHtmlAndUntouchedInPlainText() {
        EmailService.Mail mail = emailService.verificationMail("<script>alert(1)</script>", "tok");

        assertThat(mail.html()).doesNotContain("<script>")
                .contains("Ciao &lt;script&gt;alert(1)&lt;/script&gt;, ti diamo il benvenuto!");
        assertThat(mail.text()).startsWith("Ciao <script>alert(1)</script>, ti diamo il benvenuto!");
    }

    @Test
    void linkValidityFollowsTheConfiguration() {
        props.getLimits().setVerificationLinkHours(48);
        props.getLimits().setResetLinkHours(2);

        assertThat(emailService.verificationMail("Mario", "t").html()).contains("Il link vale 48 ore.");
        assertThat(emailService.passwordResetMail("Mario", "t").html())
                .contains("Il link vale 2 ore e si può usare")
                .contains("Il link vale 2 ore.</div>");
    }

    @Test
    void subjectsComeFromTheConfiguration() {
        props.getMail().setVerificationSubject("Oggetto scelto da ABC");

        EmailService.Mail mail = emailService.verificationMail("Mario", "t");

        assertThat(mail.subject()).isEqualTo("Oggetto scelto da ABC");
        assertThat(mail.html()).contains("<title>Oggetto scelto da ABC</title>");
    }

    @Test
    void fromHeaderIsTheConfiguredSenderWithItsName() throws Exception {
        emailService.sendPasswordResetEmail("socio@example.com", "Mario", "tok");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        assertThat(message.getHeader("From", null)).isEqualTo("ABC Musical Company <info@attoriballerinicantanti.it>");
        assertThat(message.getReplyTo()[0].toString()).isEqualTo(message.getHeader("From", null));
    }

    @Test
    void replyToIsSetOnlyWhenConfigured() throws Exception {
        props.getMail().setReplyTo("segreteria@attoriballerinicantanti.it");

        emailService.sendVerificationEmail("socio@example.com", "Mario", "tok");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        assertThat(message.getHeader("Reply-To", null)).isEqualTo("segreteria@attoriballerinicantanti.it");
    }

    @Test
    void aTemplateInTheExternalFolderWinsOverThePackagedOne(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("verifica.txt.ftl"), "Testo scelto da ABC per ${name}: ${link}");
        props.getMail().setTemplatesDir(dir.toString());
        EmailService custom = new EmailService(mailSender, posters, new EmailTemplates(props), props);

        EmailService.Mail mail = custom.verificationMail("Mario", "tok");

        assertThat(mail.text()).isEqualTo("Testo scelto da ABC per Mario: https://api.abc.example/api/auth/verify?token=tok");
        // gli altri modelli restano quelli del pacchetto
        assertThat(mail.html()).contains("Ciao Mario, ti diamo il benvenuto!");
        assertThat(custom.passwordResetMail("Mario", "tok").text()).startsWith("Ciao Mario, ecco il link");
    }

    @Test
    void anExternalLayoutChangesBothMails(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("layout.ftlh"),
                "<#macro page title preheader eyebrow heading buttonLabel link note>[${heading}]<#nested></#macro>");
        props.getMail().setTemplatesDir(dir.toString());
        EmailService custom = new EmailService(mailSender, posters, new EmailTemplates(props), props);

        assertThat(custom.verificationMail("Mario", "t").html()).startsWith("[Ciao Mario, ti diamo il benvenuto!]");
        assertThat(custom.passwordResetMail("Mario", "t").html()).startsWith("[Ciao Mario, ecco il link per la nuova password]");
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
        props.setBaseUrl("https://attoriballerinicantanti.nibius.duckdns.org");
        props.setFrontendUrl("https://attoriballerinicantanti.nibius.duckdns.org");
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

    /** Valori come nel application.yml di produzione, con gli indirizzi di prova di questi test. */
    static AppProperties testProps() {
        AppProperties p = new AppProperties();
        p.setBaseUrl("https://api.abc.example");
        p.setFrontendUrl("https://abc.example");
        p.getMail().setFrom("info@attoriballerinicantanti.it");
        p.getMail().setVerificationSubject("Conferma il tuo indirizzo email — ABC Musical Company");
        p.getMail().setResetSubject("Reimposta la password — ABC Musical Company");
        p.getMail().setTemplatesDir(Path.of(System.getProperty("java.io.tmpdir"), "abc-nessun-modello").toString());
        return p;
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

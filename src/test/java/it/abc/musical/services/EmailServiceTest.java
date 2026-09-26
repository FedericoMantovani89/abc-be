package it.abc.musical.services;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Il nome scritto in registrazione non deve diventare HTML nelle email (sicurezza #4). */
class EmailServiceTest {

    private static final String HOSTILE_NAME = "<a href=\"https://truffa.example\">clicca qui</a>";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService emailService = new EmailService(mailSender);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "baseUrl", "https://abc.example");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://abc.example");
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private String sentHtml() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        return (String) message.getContent();
    }

    @Test
    void verificationEmailEscapesTheName() throws Exception {
        emailService.sendVerificationEmail("socio@example.com", HOSTILE_NAME, "tok");

        String html = sentHtml();
        assertThat(html).doesNotContain("truffa.example\">");
        assertThat(html).contains("&lt;a href=&quot;https://truffa.example&quot;&gt;clicca qui&lt;/a&gt;");
    }

    @Test
    void resetEmailEscapesTheName() throws Exception {
        emailService.sendPasswordResetEmail("socio@example.com", HOSTILE_NAME, "tok");

        String html = sentHtml();
        assertThat(html).doesNotContain("truffa.example\">");
        assertThat(html).contains("&lt;a href=");
    }
}

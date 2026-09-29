package it.abc.musical.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MailConfigTest {

    private final Map<String, String> base = Map.of("mail.smtp.auth", "true");

    @Test
    void sslTrueMeansDirectSslAndNoStarttls() {
        AppProperties.Mail mail = new AppProperties.Mail();
        mail.setSsl(true);

        Properties p = MailConfig.javaMailProperties(base, mail);

        assertThat(p.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(p.getProperty("mail.smtp.starttls.enable")).isEqualTo("false");
        assertThat(p.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(p.getProperty("mail.smtp.auth")).isEqualTo("true");
    }

    @Test
    void sslFalseKeepsTodaysStarttlsBehaviour() {
        Properties p = MailConfig.javaMailProperties(base, new AppProperties.Mail());

        assertThat(p.getProperty("mail.smtp.ssl.enable")).isEqualTo("false");
        assertThat(p.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
    }

    @Test
    void connectionReadAndWriteAreBoundedSoASlowServerCannotBlock() {
        AppProperties.Mail mail = new AppProperties.Mail();
        mail.setConnectionTimeoutMs(3000);
        mail.setReadTimeoutMs(4000);
        mail.setWriteTimeoutMs(5000);

        Properties p = MailConfig.javaMailProperties(base, mail);

        assertThat(p.getProperty("mail.smtp.connectiontimeout")).isEqualTo("3000");
        assertThat(p.getProperty("mail.smtp.timeout")).isEqualTo("4000");
        assertThat(p.getProperty("mail.smtp.writetimeout")).isEqualTo("5000");
        assertThat(MailConfig.javaMailProperties(base, new AppProperties.Mail()).getProperty("mail.smtp.timeout"))
                .isEqualTo("10000");
    }
}

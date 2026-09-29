package it.abc.musical.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Il mittente SMTP. Sostituisce quello di Spring Boot per due motivi: SMTP_SSL sceglie fra SSL
 * diretto (Aruba, porta 465) e STARTTLS (587, sviluppo), e i tempi massimi impediscono che un
 * server di posta lento blocchi l'iscrizione.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    JavaMailSenderImpl mailSender(MailProperties mail, AppProperties props) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.getHost());
        if (mail.getPort() != null) {
            sender.setPort(mail.getPort());
        }
        sender.setUsername(mail.getUsername());
        sender.setPassword(mail.getPassword());
        sender.setDefaultEncoding(mail.getDefaultEncoding().name());
        sender.setJavaMailProperties(javaMailProperties(mail.getProperties(), props.getMail()));
        warnIfSenderDiffersFromLogin(props.getMail().getFrom(), mail.getUsername());
        return sender;
    }

    /** Proprieta' di Jakarta Mail: quelle di spring.mail.properties piu' SSL/STARTTLS e timeout. */
    static Properties javaMailProperties(java.util.Map<String, String> base, AppProperties.Mail mail) {
        Properties p = new Properties();
        p.putAll(base);
        p.setProperty("mail.smtp.ssl.enable", String.valueOf(mail.isSsl()));
        p.setProperty("mail.smtp.starttls.enable", String.valueOf(!mail.isSsl()));
        p.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        p.setProperty("mail.smtp.connectiontimeout", String.valueOf(mail.getConnectionTimeoutMs()));
        p.setProperty("mail.smtp.timeout", String.valueOf(mail.getReadTimeoutMs()));
        p.setProperty("mail.smtp.writetimeout", String.valueOf(mail.getWriteTimeoutMs()));
        return p;
    }

    /** Aruba rifiuta i messaggi con un mittente diverso dall'utente SMTP: meglio dirlo all'avvio. */
    static void warnIfSenderDiffersFromLogin(String from, String smtpUser) {
        if (smtpUser != null && !smtpUser.isBlank() && !smtpUser.equalsIgnoreCase(from)) {
            log.warn("MAIL_FROM ({}) e' diverso da SMTP_USER ({}): molti server di posta, Aruba compreso, "
                    + "rifiutano un mittente che non coincide con l'utente SMTP", from, smtpUser);
        }
    }
}

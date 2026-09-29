package it.abc.musical.config;

import it.abc.musical.services.EmailPosterService;
import it.abc.musical.services.EmailService;
import it.abc.musical.services.EmailTemplates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * La configurazione unica {@code app.*} letta dal vero application.yml, senza database:
 * valori obbligatori, valori predefiniti di produzione e file esterni in /config.
 */
class ConfigurazioneTest {

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class SoloProprieta {
    }

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class ConEmail {
        @Bean
        JavaMailSender mailSender() {
            return mock(JavaMailSender.class);
        }

        @Bean
        EmailPosterService posters() {
            return mock(EmailPosterService.class);
        }
    }

    private static String cartellaInesistente() {
        return "APP_CONFIG_DIR=" + Path.of(System.getProperty("java.io.tmpdir"), "abc-non-esiste");
    }

    private ApplicationContextRunner runner(Class<?>... config) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(config)
                .withSystemProperties(cartellaInesistente())
                .withPropertyValues("JWT_SECRET=dGVzdC1zZWNyZXQtMzItYnl0ZXMtbG9uZy1mb3ItaHMyNTYhIQ==",
                        "REMEMBER_ME_KEY=chiave-di-prova");
    }

    @Test
    void productionDefaultsAreTheOnesOfTheAssociation() {
        runner(SoloProprieta.class).run(ctx -> {
            AppProperties p = ctx.getBean(AppProperties.class);
            assertThat(p.getBaseUrl()).isEqualTo("https://www.attoriballerinicantanti.it");
            assertThat(p.getFrontendUrl()).isEqualTo("https://www.attoriballerinicantanti.it");
            assertThat(p.getMail().getFrom()).isEqualTo("info@attoriballerinicantanti.it");
            assertThat(p.getMail().getFromName()).isEqualTo("ABC Musical Company");
            assertThat(p.getMail().getReplyTo()).isEmpty();
            assertThat(p.getMail().isSsl()).isFalse();
            assertThat(p.getMail().getConnectionTimeoutMs()).isEqualTo(10_000);
            assertThat(p.getAssociazione().getNome()).isEqualTo("ABC - Attori Ballerini Cantanti APS");
            assertThat(p.getAssociazione().getEmail()).isEqualTo("info@attoriballerinicantanti.it");
            assertThat(p.getAssociazione().getSede()).isEqualTo("Via Interna Molini 1C, 37132 Verona (VR)");
            assertThat(p.getAssociazione().getCf()).isEqualTo("93242450232");
            assertThat(p.getAssociazione().getPiva()).isEqualTo("04443330230");
            assertThat(p.getAssociazione().getRunts()).isEqualTo("DA_INSERIRE");
            assertThat(p.getCors().getExtraOrigins()).isEmpty();
            assertThat(p.getLimits().getVerificationLinkHours()).isEqualTo(24);
            assertThat(p.getLimits().getResetLinkHours()).isEqualTo(1);
            assertThat(p.getLimits().getAuthRequestsPerHour()).isEqualTo(20);
        });
    }

    @Test
    void backendHidesInternalDetailsAndExposesOnlyHealth() {
        runner(SoloProprieta.class).run(ctx -> {
            var env = ctx.getEnvironment();
            assertThat(env.getProperty("management.endpoints.web.exposure.include")).startsWith("health");
            assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
            assertThat(env.getProperty("server.error.include-stacktrace")).isEqualTo("never");
            assertThat(env.getProperty("server.error.include-message")).isEqualTo("never");
            assertThat(env.getProperty("server.error.include-binding-errors")).isEqualTo("never");
        });
    }

    @Test
    void missingRequiredValueStopsStartupAndNamesTheProperty() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(SoloProprieta.class)
                .withSystemProperties(cartellaInesistente())
                .withPropertyValues("REMEMBER_ME_KEY=chiave-di-prova")   // manca JWT_SECRET
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(rootMessages(ctx.getStartupFailure())).contains("app.jwt.secret");
                });
    }

    @Test
    void emptyMailSenderStopsStartup() {
        runner(SoloProprieta.class).withPropertyValues("MAIL_FROM=").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(rootMessages(ctx.getStartupFailure())).contains("app.mail.from");
        });
    }

    @Test
    void externalConfigFolderOverridesTheSubjectAndTheEmailUsesIt(@TempDir Path config) throws Exception {
        Files.writeString(config.resolve("application-prod.yml"),
                "app:\n  mail:\n    verification-subject: \"Benvenuto in ABC, conferma qui\"\n"
                        + "  associazione:\n    runts: \"12345\"\n", StandardCharsets.UTF_8);
        runner(ConEmail.class, EmailTemplates.class, EmailService.class)
                .withSystemProperties("APP_CONFIG_DIR=" + config)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    AppProperties p = ctx.getBean(AppProperties.class);
                    assertThat(p.getMail().getVerificationSubject()).isEqualTo("Benvenuto in ABC, conferma qui");
                    assertThat(p.getAssociazione().getRunts()).isEqualTo("12345");
                    // il resto resta quello del pacchetto
                    assertThat(p.getMail().getResetSubject()).isEqualTo("Reimposta la password — ABC Musical Company");
                    assertThat(p.getMail().getTemplatesDir()).isEqualTo(config + "/email");
                });
    }

    @Test
    void externalMessagesFileOverridesAnErrorText(@TempDir Path config) throws Exception {
        Files.writeString(config.resolve("messages.properties"),
                "auth.token.scaduto=Il link è scaduto: chiedine uno nuovo\n", StandardCharsets.UTF_8);
        try {
            runner(SoloProprieta.class, MessagesConfig.class)
                    .withSystemProperties("APP_CONFIG_DIR=" + config)
                    .run(ctx -> {
                        assertThat(ctx).hasNotFailed();
                        assertThat(Messages.text("auth.token.scaduto")).isEqualTo("Il link è scaduto: chiedine uno nuovo");
                        // le altre frasi restano quelle del pacchetto
                        assertThat(Messages.text("auth.token.non.valido")).isEqualTo("Token non valido");
                    });
        } finally {
            // rimette la sorgente del solo pacchetto per gli altri test di questa JVM
            Messages.use(Messages.build("classpath:messages"));
        }
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t).append('\n');
        }
        return all.toString();
    }
}

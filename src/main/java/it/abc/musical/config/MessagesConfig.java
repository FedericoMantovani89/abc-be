package it.abc.musical.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Sorgente dei testi: /config/messages.properties (se esiste) ha la precedenza su quello del
 * pacchetto, cosi' ABC puo' cambiare una frase senza una nuova versione. Basta il riavvio.
 */
@Configuration
public class MessagesConfig {

    @Bean
    MessageSource messageSource(Environment environment) {
        String configDir = environment.getProperty("app.config-dir", "/config");
        String external = java.nio.file.Path.of(configDir).toAbsolutePath().toUri().toString().replaceAll("/+$", "");
        var messages = Messages.build(external + "/messages", "classpath:messages");
        Messages.use(messages);
        return messages;
    }
}

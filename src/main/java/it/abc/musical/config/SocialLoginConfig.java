package it.abc.musical.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Accessi Google e Facebook FACOLTATIVI. Un provider si registra solo se id e segreto sono
 * compilati: senza chiavi il sito parte comunque, senza quel pulsante di accesso. La registrazione
 * automatica di Spring Boot (spring.security.oauth2.client.*) rifiuta invece id vuoti e blocca
 * l'avvio, per questo qui i provider si costruiscono a mano da {@code app.social.*}.
 */
@Slf4j
@Configuration
public class SocialLoginConfig {

    /** Nomi dei provider attivi, per l'elenco pubblico e per il pulsante nel frontend. */
    public record SocialProviders(Set<String> attivi) {
        public boolean isActive(String id) {
            return attivi.contains(id);
        }
    }

    @Bean
    SocialProviders socialProviders(AppProperties props) {
        Set<String> attivi = new java.util.LinkedHashSet<>();
        if (props.getSocial().getGoogle().isConfigured()) attivi.add("google");
        if (props.getSocial().getFacebook().isConfigured()) attivi.add("facebook");
        log.info("Accessi social attivi: {}", attivi.isEmpty() ? "nessuno (chiavi non impostate)" : String.join(", ", attivi));
        warnIfHalfConfigured("google", props.getSocial().getGoogle());
        warnIfHalfConfigured("facebook", props.getSocial().getFacebook());
        return new SocialProviders(attivi);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(AppProperties props) {
        return registrations(props);
    }

    static ClientRegistrationRepository registrations(AppProperties props) {
        List<ClientRegistration> list = new ArrayList<>();
        AppProperties.Provider g = props.getSocial().getGoogle();
        if (g.isConfigured()) {
            list.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(g.getClientId().strip()).clientSecret(g.getClientSecret().strip())
                    .redirectUri(props.getBaseUrl() + "/login/oauth2/code/google")
                    .scope("openid", "email", "profile").build());
        }
        AppProperties.Provider f = props.getSocial().getFacebook();
        if (f.isConfigured()) {
            list.add(CommonOAuth2Provider.FACEBOOK.getBuilder("facebook")
                    .clientId(f.getClientId().strip()).clientSecret(f.getClientSecret().strip())
                    .redirectUri(props.getBaseUrl() + "/login/oauth2/code/facebook")
                    .scope("email", "public_profile").build());
        }
        // Senza provider InMemoryClientRegistrationRepository rifiuta la lista vuota: ne serve una che risponda "nessuno".
        if (list.isEmpty()) {
            return registrationId -> null;
        }
        return new InMemoryClientRegistrationRepository(list);
    }

    private static void warnIfHalfConfigured(String nome, AppProperties.Provider p) {
        boolean id = p.getClientId() != null && !p.getClientId().isBlank();
        boolean secret = p.getClientSecret() != null && !p.getClientSecret().isBlank();
        if (id != secret) {
            log.warn("Accesso {} NON attivo: e' compilato solo uno tra id e segreto.", nome);
        }
    }
}

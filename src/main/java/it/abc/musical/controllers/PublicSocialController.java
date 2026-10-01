package it.abc.musical.controllers;

import it.abc.musical.config.AppProperties;
import it.abc.musical.config.SocialLoginConfig.SocialProviders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

/** Accessi social facoltativi: quali sono attivi, e risposta chiara se si prova uno spento. */
@RestController
@RequiredArgsConstructor
public class PublicSocialController {

    private final SocialProviders providers;
    private final AppProperties props;

    /** Per il frontend: nascondere il pulsante dei provider non attivi. */
    @GetMapping("/api/public/auth/social")
    public Map<String, Boolean> attivi() {
        return Map.of("google", providers.isActive("google"), "facebook", providers.isActive("facebook"));
    }

    /**
     * Raggiunta SOLO per un provider non registrato (quelli attivi li gestisce Spring Security
     * prima di arrivare qui): torna al login con un errore chiaro invece di un 404/500.
     */
    @GetMapping("/oauth2/authorization/{provider}")
    public RedirectView providerNonAttivo(@PathVariable String provider) {
        return new RedirectView(props.getFrontendUrl() + "/login?error=social_non_attivo", false);
    }
}

package it.abc.musical.controllers;

import it.abc.musical.config.SocialLoginConfig.SocialProviders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Accessi social facoltativi: quali sono attivi. */
@RestController
@RequiredArgsConstructor
public class PublicSocialController {

    private final SocialProviders providers;

    /** Per il frontend: nascondere il pulsante dei provider non attivi. */
    @GetMapping("/api/public/auth/social")
    public Map<String, Boolean> attivi() {
        return Map.of("google", providers.isActive("google"), "facebook", providers.isActive("facebook"));
    }
}

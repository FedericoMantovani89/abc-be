package it.abc.musical.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** Accessi social facoltativi: nessuna chiave, solo Google, solo Facebook, entrambe. */
class SocialLoginConfigTest {

    private static AppProperties props(String gId, String gSecret, String fId, String fSecret) {
        AppProperties p = new AppProperties();
        p.setBaseUrl("https://sito.test");
        p.getSocial().getGoogle().setClientId(gId);
        p.getSocial().getGoogle().setClientSecret(gSecret);
        p.getSocial().getFacebook().setClientId(fId);
        p.getSocial().getFacebook().setClientSecret(fSecret);
        return p;
    }

    @Test
    void nessunaChiaveNessunProviderEIlRepositoryRispondeNullSenzaErrori() {
        AppProperties p = props("", "", "", "");
        ClientRegistrationRepository repo = SocialLoginConfig.registrations(p);

        assertThat(repo.findByRegistrationId("google")).isNull();
        assertThat(repo.findByRegistrationId("facebook")).isNull();
        assertThat(new SocialLoginConfig().socialProviders(p).attivi()).isEmpty();
    }

    @Test
    void soloGoogle() {
        AppProperties p = props("g-id", "g-secret", "", "");
        ClientRegistrationRepository repo = SocialLoginConfig.registrations(p);

        assertThat(repo.findByRegistrationId("google").getClientId()).isEqualTo("g-id");
        assertThat(repo.findByRegistrationId("google").getRedirectUri()).isEqualTo("https://sito.test/login/oauth2/code/google");
        assertThat(repo.findByRegistrationId("facebook")).isNull();
        assertThat(new SocialLoginConfig().socialProviders(p).attivi()).containsExactly("google");
    }

    @Test
    void soloFacebook() {
        AppProperties p = props("", "", "f-id", "f-secret");
        ClientRegistrationRepository repo = SocialLoginConfig.registrations(p);

        assertThat(repo.findByRegistrationId("facebook").getClientId()).isEqualTo("f-id");
        assertThat(repo.findByRegistrationId("google")).isNull();
        assertThat(new SocialLoginConfig().socialProviders(p).attivi()).containsExactly("facebook");
    }

    @Test
    void entrambi() {
        AppProperties p = props("g-id", "g-secret", "f-id", "f-secret");
        ClientRegistrationRepository repo = SocialLoginConfig.registrations(p);

        assertThat(repo.findByRegistrationId("google")).isNotNull();
        assertThat(repo.findByRegistrationId("facebook")).isNotNull();
        assertThat(new SocialLoginConfig().socialProviders(p).attivi()).containsExactly("google", "facebook");
    }

    @Test
    void conSoloIdOSoloSegretoIlProviderNonSiAttiva() {
        AppProperties p = props("g-id", "", "", "f-secret");

        assertThat(SocialLoginConfig.registrations(p).findByRegistrationId("google")).isNull();
        assertThat(SocialLoginConfig.registrations(p).findByRegistrationId("facebook")).isNull();
    }
}

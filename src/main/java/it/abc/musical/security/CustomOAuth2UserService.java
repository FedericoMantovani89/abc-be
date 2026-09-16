package it.abc.musical.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/** Facebook (OAuth2 puro, non OIDC): provisioning dell'utente locale al login. */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuth2UserProvisioningService provisioningService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        String name = oauth2User.getAttribute("name");
        String firstName = null;
        String lastName = null;
        if (name != null && name.contains(" ")) {
            int idx = name.indexOf(' ');
            firstName = name.substring(0, idx);
            lastName = name.substring(idx + 1);
        } else {
            firstName = name;
        }
        provisioningService.provision(
                "facebook",
                oauth2User.getName(),          // id Facebook
                oauth2User.getAttribute("email"),
                firstName,
                lastName,
                null);
        return oauth2User;
    }
}
